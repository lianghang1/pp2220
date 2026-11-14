package io.github.lianghang1.paygateway.model;

/**
 * 网关内四类业务单据的归类标识：用于事件、通知记录、对账比对时的单据定位。
 *
 * @author lianghang
 */
public enum BizOrderType {

    /** 支付单 */
    PAYMENT,
    /** 退款单 */
    REFUND,
    /** 转账单 */
    TRANSFER,
    /** 分账单 */
    PROFIT_SHARE
}
