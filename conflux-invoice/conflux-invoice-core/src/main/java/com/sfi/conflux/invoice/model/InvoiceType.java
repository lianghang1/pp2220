package com.sfi.conflux.invoice.model;

/**
 * 框架统一的发票票种语义。
 *
 * <p>框架只定义语义，不定义各渠道的票种编码（如 fplxdm）：编码映射由渠道包在
 * {@code InvoiceIssuer} 内部完成，避免把某一家的字典泄漏到公共模型。</p>
 *
 * @author lianghang
 */
public enum InvoiceType {

    /** 数电票（增值税专用发票）。 */
    DIGITAL_VAT_SPECIAL("数电票-增值税专用发票"),
    /** 数电票（普通发票）。 */
    DIGITAL_VAT_GENERAL("数电票-普通发票"),
    /** 增值税专用发票（非数电）。 */
    VAT_SPECIAL("增值税专用发票"),
    /** 增值税普通发票（非数电）。 */
    VAT_GENERAL("增值税普通发票"),
    /** 机动车销售统一发票。 */
    VEHICLE_SALES("机动车销售统一发票"),
    /** 其他/渠道自定义票种。 */
    OTHER("其他票种");

    private final String label;

    InvoiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
