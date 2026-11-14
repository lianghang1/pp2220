package io.github.lianghang1.conflux.pay.model;

/**
 * 支付单状态机。
 *
 * <p>迁移约束（由引擎内部保证，Store 实现方无需校验）：
 * {@code CREATED → PAYING → SUCCEEDED / FAILED / CLOSED}；
 * {@code SUCCEEDED → REFUNDING → PART_REFUNDED → REFUNDED}；
 * PAYING 状态下允许由渠道查询/对账修正为终态。</p>
 *
 * @author lianghang
 */
public enum PaymentState {

    /** 已创建，尚未发起渠道支付 */
    CREATED,
    /** 已发起渠道支付，等待用户支付或渠道确认 */
    PAYING,
    /** 支付成功（终态） */
    SUCCEEDED,
    /** 支付失败（终态） */
    FAILED,
    /** 已关闭（终态） */
    CLOSED,
    /** 退款处理中 */
    REFUNDING,
    /** 部分退款（终态，仍可继续退款） */
    PART_REFUNDED,
    /** 全额退款（终态） */
    REFUNDED;

    /** 是否终态（PART_REFUNDED 视为终态，但允许继续退款）。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CLOSED
                || this == PART_REFUNDED || this == REFUNDED;
    }

    /** 是否已成功（含退款后状态）。 */
    public boolean isPaid() {
        return this == SUCCEEDED || this == REFUNDING
                || this == PART_REFUNDED || this == REFUNDED;
    }
}
