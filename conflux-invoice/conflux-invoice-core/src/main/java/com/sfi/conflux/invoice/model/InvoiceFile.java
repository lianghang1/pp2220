package com.sfi.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 发票版式文件。
 *
 * <p>{@code content} 用于内联小文件（XML/OFD 通常可直接内联），{@code url} 用于
 * 渠道只给下载地址的大文件；二者至少其一非空。{@code digest} 为内容摘要，
 * 由编排器在归档前计算，保证法定原件未被篡改且可核对。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件标识。 */
    private String fileId;
    /** 所属发票单据。 */
    private String invoiceId;
    /** 文件类型。 */
    private InvoiceFileType type;
    /** 内联内容（XML 原件的文本或 Base64）。 */
    private String content;
    /** 下载地址（渠道未提供内联内容时使用）。 */
    private String url;
    /** 内容摘要（SHA-256 十六进制，对固化后字节计算）。 */
    private String digest;
    /** 内容字节数（未知时为 -1）。 */
    private long size = -1L;
    /** 本地落盘路径（配置 outputDir 且固化成功时回填，未落盘为 null）。 */
    private String filePath;

    /** 是否为法定原件。 */
    public boolean isLegalOriginal() {
        return type != null && type.isLegalOriginal();
    }

    /** 是否有可用内容（内联或下载地址）。 */
    public boolean hasPayload() {
        return (content != null && content.length() > 0) || (url != null && url.length() > 0);
    }

    /** 是否已固化为内联字节（content 非空且摘要/尺寸已核算）。 */
    public boolean isMaterialized() {
        return content != null && content.length() > 0 && digest != null;
    }
}
