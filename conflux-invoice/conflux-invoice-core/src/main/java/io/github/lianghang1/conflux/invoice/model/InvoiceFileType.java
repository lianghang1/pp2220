package io.github.lianghang1.conflux.invoice.model;

/**
 * 发票版式文件类型。
 *
 * <p><b>XML 是数电票的法定原件</b>，不是可选的展示附件：渠道回传开票成功时
 * 必须能取到 XML，否则框架拒绝把单据置为 {@code SUCCEEDED}
 * （{@code InvoiceErrorCode.XML_ORIGINAL_REQUIRED}）。这一约束在支付域不存在。</p>
 *
 * @author lianghang
 */
public enum InvoiceFileType {

    /** 法定原件（数电票 XML）。 */
    XML("数电票 XML 法定原件", true),
    /** OFD 版式文件。 */
    OFD("OFD 版式文件", false),
    /** PDF 版式文件。 */
    PDF("PDF 版式文件", false),
    /** 图片（二维码/票面图）。 */
    IMAGE("票面图片", false);

    private final String label;
    private final boolean legalOriginal;

    InvoiceFileType(String label, boolean legalOriginal) {
        this.label = label;
        this.legalOriginal = legalOriginal;
    }

    public String getLabel() {
        return label;
    }

    /** 是否为法定原件；开票成功必须归档至少一份。 */
    public boolean isLegalOriginal() {
        return legalOriginal;
    }
}
