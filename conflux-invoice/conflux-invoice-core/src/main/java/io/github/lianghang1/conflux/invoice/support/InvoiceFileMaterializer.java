package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * 版式文件固化器：把渠道交付的两种形态（内联 Base64 / 下载地址）统一拉平为字节。
 *
 * <p><b>归档即固化</b>：渠道下载链接普遍有时效，归档那一刻最新鲜可靠，
 * 因此编排层在把单据置为成功前就地完成固化——XML 法定原件失败一律阻断置成功，
 * 展示文件（PDF/OFD/IMAGE）失败仅告警保留 url，可由 {@code fileBytes} 或修复补拉兜底。</p>
 *
 * <p>本类不做任何「文件再加工」（转图/A4 重组），那是门面按需开放的能力。</p>
 *
 * @author lianghang
 */
@Slf4j
public class InvoiceFileMaterializer {

    private final InvoiceFileOptions options;
    private final PersistCallback persist;

    /** 固化结果回写持久层（幂等）；null 表示仅就地修改不回写（如调用方自行批量保存）。 */
    public interface PersistCallback {
        void persist(List<InvoiceFile> files);
    }

    public InvoiceFileMaterializer(InvoiceFileOptions options, PersistCallback persist) {
        this.options = options == null ? new InvoiceFileOptions() : options;
        this.persist = persist;
    }

    /**
     * 就地固化文件列表。
     *
     * @param strict true = 任何 XML 固化失败立即抛异常（编排层在置成功前调用）；
     *               false = 全部失败仅告警（回调补充场景，绝不让回调失败）
     */
    public void materialize(List<InvoiceFile> files, boolean strict) {
        if (files == null || files.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (InvoiceFile file : files) {
            if (file == null || file.getType() == null || !file.hasPayload()) {
                continue;
            }
            if (file.isMaterialized()) {
                ensureDiskCopy(file);
                continue;
            }
            try {
                materializeOne(file, strict);
                changed = true;
            } catch (GatewayException e) {
                // 两级失败策略：strict 仅对 XML 法定原件生效（失败阻断置成功）；
                // PDF/OFD/IMAGE 展示文件下载失败一律只告警、保留 url 待 fileBytes/repair 补拉。
                if (strict && file.getType() == InvoiceFileType.XML) {
                    throw e;
                }
                log.warn("版式文件固化失败（不阻断）: invoiceId={}, type={}, url={}, error={}",
                        file.getInvoiceId(), file.getType(), file.getUrl(), e.getMessage());
            }
        }
        if (changed && persist != null) {
            persist.persist(files);
        }
    }

    /** 单文件取原始字节（优先内联 content，其次 url 下载；url 失效抛 FILE_URL_EXPIRED）。 */
    public byte[] materializeBytes(InvoiceFile file) {
        GatewayAssert.notNull(file, InvoiceErrorCode.FILE_NOT_FOUND, "版式文件不存在");
        if (file.isMaterialized()) {
            return decode(file);
        }
        if (file.getContent() != null && file.getContent().length() > 0) {
            byte[] bytes = decode(file);
            applyDigest(file, bytes);
            if (persist != null) {
                persist.persist(java.util.Collections.singletonList(file));
            }
            return bytes;
        }
        GatewayAssert.notBlank(file.getUrl(), InvoiceErrorCode.FILE_NOT_FOUND,
                "版式文件既无内联内容也无下载地址: {}", file.getFileId());
        byte[] bytes = download(file);
        // 下载后必须回写持久层（FileStore 返回的是拷贝对象）：否则下次 fileBytes 仍是
        // url-only，会重复下载。回写幂等，第二次命中 isMaterialized 直接解码。
        if (persist != null) {
            persist.persist(java.util.Collections.singletonList(file));
        }
        return bytes;
    }

    /** 按类型取扩展名（IMAGE 统一 img，具体格式以内容为准）。 */
    public static String extensionOf(InvoiceFileType type) {
        switch (type) {
            case XML:
                return "xml";
            case OFD:
                return "ofd";
            case PDF:
                return "pdf";
            case IMAGE:
            default:
                return "img";
        }
    }

    /** 字节 → 内联 content：XML 存 UTF-8 文本，其余存 Base64。 */
    public static String encodeContent(InvoiceFileType type, byte[] bytes) {
        if (type == InvoiceFileType.XML) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return Base64.getMimeEncoder().encodeToString(bytes);
    }

    /** 内联 content → 字节：XML 按 UTF-8 文本，其余按 MIME Base64 解码（容忍换行空白）。 */
    public static byte[] decode(InvoiceFile file) {
        GatewayAssert.notNull(file.getType(), InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                "文件类型缺失，无法解码: {}", file.getFileId());
        GatewayAssert.notBlank(file.getContent(), InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                "文件内联内容为空: {}", file.getFileId());
        if (file.getType() == InvoiceFileType.XML) {
            return file.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            return Base64.getMimeDecoder().decode(file.getContent());
        } catch (IllegalArgumentException e) {
            throw new GatewayException(InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                    "文件 Base64 解码失败: fileId={}", file.getFileId(), e);
        }
    }

    private void materializeOne(InvoiceFile file, boolean strict) {
        boolean urlOnly = file.getContent() == null || file.getContent().length() == 0;
        if (urlOnly) {
            if (!options.isImmediateDownload()) {
                // 关闭即时固化：仅展示文件允许保留 url；XML 仍强制下载（法定底线）
                if (file.getType() != InvoiceFileType.XML) {
                    log.info("即时固化已关闭，url 型文件暂存链接: invoiceId={}, type={}",
                            file.getInvoiceId(), file.getType());
                    return;
                }
            }
            download(file);
        } else {
            applyDigest(file, decode(file));
        }
        ensureDiskCopy(file);
    }

    /** 下载 url 并回填 content/digest/size；失败按文件性质抛错。 */
    private byte[] download(InvoiceFile file) {
        String url = file.getUrl();
        HttpResponse response;
        try {
            response = HttpTransport.getWithRetry(url, null,
                    10000L, options.getDownloadTimeoutMillis(), 10000L);
        } catch (RuntimeException e) {
            throwFailure(file, "下载 IO 异常: " + e.getMessage());
            return null; // 不可达，编译器满足
        }
        if (!response.isSuccessful()) {
            // 4xx/5xx：链接失效或渠道临时故障，统一按失效语义抛出，引导修复补拉
            throwFailure(file, "HTTP " + response.getStatusCode());
        }
        byte[] bytes = response.getRawBytes();
        GatewayAssert.isTrue(bytes.length > 0, InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                "下载内容为空: fileId={}", file.getFileId());
        file.setContent(encodeContent(file.getType(), bytes));
        applyDigest(file, bytes);
        return bytes;
    }

    private void throwFailure(InvoiceFile file, String detail) {
        if (file.getType() == InvoiceFileType.XML) {
            // 法定原件：任何失败都是阻断级（编排层在置成功前调用，异常阻止 SUCCEEDED）
            throw new GatewayException(InvoiceErrorCode.XML_ORIGINAL_REQUIRED,
                    "XML 法定原件固化失败（{}）: invoiceId={}", detail, file.getInvoiceId());
        }
        throw new GatewayException(InvoiceErrorCode.FILE_URL_EXPIRED,
                "版式文件下载失效（{}）: fileId={}, url={}", detail, file.getFileId(), file.getUrl());
    }

    private void applyDigest(InvoiceFile file, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        file.setDigest(SignUtil.sha256Hex(bytes));
        file.setSize(bytes.length);
    }

    /** 配置了落盘目录时写字节到本地（失败仅告警不阻断，字节已在 FileStore）。 */
    private void ensureDiskCopy(InvoiceFile file) {
        if (options.getOutputDir() == null || file.getFilePath() != null) {
            return;
        }
        if (file.getContent() == null || file.getContent().length() == 0) {
            return;
        }
        try {
            byte[] bytes = decode(file);
            java.io.File target = options.writeToFile(file.getInvoiceId(), file.getFileId(),
                    extensionOf(file.getType()), bytes);
            file.setFilePath(target.getAbsolutePath());
            log.info("版式文件已落盘: {}", target.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            log.warn("版式文件落盘失败（不阻断）: fileId={}, error={}",
                    file.getFileId(), e.getMessage());
        }
    }

    /** 供测试/手动装配使用的无持久化构造。 */
    public static InvoiceFileMaterializer detached(InvoiceFileOptions options) {
        return new InvoiceFileMaterializer(options, null);
    }
}
