package com.sfi.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 发票明细行（框架统一模型）。
 *
 * <p>金额单位为「元」，建议保留 2 位小数；税收分类编码由渠道映射为各自字段名。
 * 渠道个性字段放 {@link #attr}，框架零感知。</p>
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
    /**
     * 零税率标识：0 正常税率（默认）、1 免税、2 不征税、3 普通零税率。
     *
     * <p>百旺等渠道明细行必填；为空时渠道适配器按 0（正常税率）上报。</p>
     */
    private String lslbs;
    /** 优惠政策标识：0 不使用（默认）、1 使用；为 1 时 {@link #zzstsgl} 必填。 */
    private String yhzcbs;
    /** 增值税特殊管理（如「免税」「即征即退 50%」「按 3% 简易征收」），优惠政策标识为 1 时必填。 */
    private String zzstsgl;
    /** 渠道个性字段。 */
    private Map<String, String> attr;
}
