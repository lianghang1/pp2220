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
 * <p><b>即时固化</b>：渠道下载链接普遍有时效，取到结果时趁链接有效就地拉平为字节、
 * 回填 content/digest/size。固化是纯工具能力：成功则载体字节齐全、使用方无需关心渠道给的是
 * url 还是 Base64；失败一律告警并保留 url，由 {@code fileBytes}/{@code repair} 补拉，
 * <b>不影响渠道返回的单据状态</b>（渠道说成功即成功，框架不否决）。</p>
 *
 * <p>本类不做任何「文件再加工」（转图/A4 重组），那是门面按需开放的能力。</p>
 *
 * @author lianghang
 */
@Slf4j
public class InvoiceFileMaterializer {

    private final InvoiceFileOptions options;

    public InvoiceFileMaterializer(InvoiceFileOptions options) {
        this.options = options == null ? new InvoiceFileOptions() : options;
    }

    /**
     * 就地固化文件列表。任何文件固化失败都只告警并保留 url，不抛异常、不影响单据状态。
     *
     * @param strict 兼容保留参数，已不影响行为（原用于控制 XML 失败是否阻断，现统一不阻断）；
     *               是否即时下载由 {@code immediate-download} 配置决定。
     */
    public void materialize(List<InvoiceFile> files, boolean strict) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (InvoiceFile file : files) {
            if (file == null || file.getType() == null || !file.hasPayload()) {
                continue;
            }
            if (file.isMaterialized()) {
                ensureDiskCopy(file);
                continue;
            }
            try {
                materializeOne(file);
            } catch (GatewayException e) {
                // 固化是工具能力，失败一律告警、保留 url 待 fileBytes/repair 补拉；
                // 渠道状态以渠道返回为准，框架不因拿不到版式文件而否决开票成功。
                log.warn("版式文件固化失败（不阻断）: invoiceId={}, type={}, url={}, error={}",
                        file.getInvoiceId(), file.getType(), file.getUrl(), e.getMessage());
            }
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
            return bytes;
        }
        GatewayAssert.notBlank(file.getUrl(), InvoiceErrorCode.FILE_NOT_FOUND,
                "版式文件既无内联内容也无下载地址: {}", file.getFileId());
        // download 就地回填 content/digest/size，第二次 fileBytes 命中 isMaterialized 直接解码，不重复下载。
        return download(file);
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

    private void materializeOne(InvoiceFile file) {
        boolean urlOnly = file.getContent() == null || file.getContent().length() == 0;
        if (urlOnly) {
            // 是否即时下载完全由配置决定，不对任何文件类型（含 XML）做特殊强制：
            // 渠道给什么、何时给，以渠道接口为准；关闭时 url 原样保留待 fileBytes 补拉。
            if (!options.isImmediateDownload()) {
                log.info("即时固化已关闭，url 型文件暂存链接: invoiceId={}, type={}",
                        file.getInvoiceId(), file.getType());
                return;
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
        // 下载失败统一按失效语义抛出，引导走 fileBytes/repair 补拉；
        // 由 materialize 调用时异常被就地告警捕获、不阻断单据状态。
        throw new GatewayException(InvoiceErrorCode.FILE_URL_EXPIRED,
                "版式文件下载失效（{}）: fileId={}, type={}, url={}",
                detail, file.getFileId(), file.getType(), file.getUrl());
    }

    private void applyDigest(InvoiceFile file, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        file.setDigest(SignUtil.sha256Hex(bytes));
        file.setSize(bytes.length);
    }

    /** 配置了落盘目录时写字节到本地（失败仅告警不阻断，字节已内联在载体）。 */
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

    /** 工厂入口：固化只就地修改载体，不持有任何台账。 */
    public static InvoiceFileMaterializer detached(InvoiceFileOptions options) {
        return new InvoiceFileMaterializer(options);
    }
}
