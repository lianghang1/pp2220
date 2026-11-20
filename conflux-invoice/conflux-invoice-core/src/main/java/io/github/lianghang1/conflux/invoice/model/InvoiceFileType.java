package io.github.lianghang1.conflux.invoice.model;

/**
 * 发票版式文件类型。
 *
 * <p><b>XML 是数电票的法定原件</b>（OFD/PDF/IMAGE 为展示件），但渠道是否提供、何时提供
 * 以对方接口为准：框架只做"能取到就固化进载体"的工具能力，<b>不把"是否拿到 XML"作为
 * 开票成功的判定条件</b>——渠道返回成功即 {@code SUCCEEDED}，由使用方按业务与合规要求决定是否补拉原件。</p>
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

    /** 是否为法定原件（仅作类型标识，不构成开票成功的判定条件）。 */
    public boolean isLegalOriginal() {
        return legalOriginal;
    }
}
