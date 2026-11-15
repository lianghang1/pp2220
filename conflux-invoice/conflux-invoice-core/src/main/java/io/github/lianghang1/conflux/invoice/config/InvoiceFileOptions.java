package io.github.lianghang1.conflux.invoice.config;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;

import java.io.File;
import java.io.IOException;

/**
 * 发票版式文件固化选项。
 *
 * <p>各渠道交付形态不一（有的给下载地址、有的给 Base64），框架统一在归档时
 * 拉平为「内联字节 + 可选落盘」，使用方拿到的永远是已固化的文件，无需感知渠道差异：</p>
 *
 * <ul>
 *   <li>{@code immediateDownload}（默认 true）：归档时对只有 {@code url} 的文件立即下载
 *       转为内联字节。渠道链接普遍有时效（1~7 天），归档那一刻最新鲜可靠；
 *       XML 法定原件不随开关变化——下载失败一律阻断置成功</li>
 *   <li>{@code outputDir}（可选）：配置后固化字节同步写盘
 *       {@code {outputDir}/{invoiceId}/{fileId}.{ext}}，并回填
 *       {@code InvoiceFile.filePath}；写盘失败仅告警不阻断（字节已在 FileStore）</li>
 * </ul>
 *
 * <p>转图/A4 重组等「文件再加工」不属于本类职责——那是按需调用的门面能力
 * （{@code InvoiceGateway#printAdaptedPdf}），框架绝不自动转换。</p>
 *
 * @author lianghang
 */
public class InvoiceFileOptions {

    /** 归档时是否对 url 型文件立即下载固化（XML 法定原件始终强制）。 */
    private boolean immediateDownload = true;

    /** 本地落盘根目录；null = 不落盘，字节仅存 FileStore。 */
    private String outputDir;

    /** 固化下载超时（毫秒）。 */
    private long downloadTimeoutMillis = 30000L;

    public boolean isImmediateDownload() {
        return immediateDownload;
    }

    public InvoiceFileOptions setImmediateDownload(boolean immediateDownload) {
        this.immediateDownload = immediateDownload;
        return this;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public InvoiceFileOptions setOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public long getDownloadTimeoutMillis() {
        return downloadTimeoutMillis;
    }

    public InvoiceFileOptions setDownloadTimeoutMillis(long downloadTimeoutMillis) {
        GatewayAssert.isTrue(downloadTimeoutMillis > 0L, InvoiceErrorCode.CONFIG_MISSING,
                "固化下载超时必须为正数");
        this.downloadTimeoutMillis = downloadTimeoutMillis;
        return this;
    }

    /** 校验 outputDir 可用性（配置了才校验），并返回标准化后的目录 File。 */
    public File requireOutputDir() {
        GatewayAssert.notBlank(outputDir, InvoiceErrorCode.CONFIG_MISSING,
                "outputDir 未配置，无法落盘");
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new GatewayException(InvoiceErrorCode.FILE_ARCHIVE_FAILED,
                    "无法创建文件落盘目录: " + outputDir);
        }
        return dir;
    }

    /** 落盘单文件（父目录按 invoiceId 分级，失败抛 IOException 由调用方决定阻断策略）。 */
    public File writeToFile(String invoiceId, String fileId, String extension,
                            byte[] bytes) throws IOException {
        File dir = requireOutputDir();
        File parent = new File(dir, invoiceId);
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建单据文件目录: " + parent);
        }
        File target = new File(parent, fileId + "." + extension);
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream(target);
            fos.write(bytes);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        return target;
    }
}
