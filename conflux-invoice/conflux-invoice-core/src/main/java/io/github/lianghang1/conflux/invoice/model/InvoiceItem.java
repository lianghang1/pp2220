package io.github.lianghang1.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 发票明细行（框架统一模型）。
 *
 * <p>金额单位为「元」，建议保留 2 位小数；税收分类编码由渠道映射为各自字段名。
 * 渠道个性字段放 {@link #attrs}，框架零感知。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 明细行号（从 1 开始）。 */
    private Integer lineNo;
    /** 项目名称/商品名称。 */
    private String itemName;
    /** 规格型号。 */
    private String specification;
    /** 单位。 */
    private String unit;
    /** 数量。 */
    private BigDecimal quantity;
    /** 单价（不含税）。 */
    private BigDecimal unitPrice;
    /** 含税金额。 */
    private BigDecimal amount;
    /** 税率（如 0.13）。 */
    private BigDecimal taxRate;
    /** 税额。 */
    private BigDecimal taxAmount;
    /** 税收分类编码。 */
    private String taxClassificationCode;
    /** 渠道个性字段。 */
    private Map<String, String> attrs;
}
