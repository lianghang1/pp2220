package io.github.lianghang1.paygateway.model;

/**
 * 渠道账单条目类型：对账时区分支付流水与退款流水。
 *
 * @author lianghang
 */
public enum BillEntryType {

    /** 支付流水 */
    PAYMENT,
    /** 退款流水 */
    REFUND
}
