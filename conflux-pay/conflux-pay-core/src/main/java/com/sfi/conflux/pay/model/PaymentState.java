package com.sfi.conflux.pay.model;

/**
 * 支付状态语义（渠道归一化结果 + 使用方业务推进参考）。
 *
 * <p>SDK 无状态机：这些状态取自渠道归一快照或由使用方在自己的业务里推进，
 * SDK 不维护迁移、不校验状态流转。典型业务链路：
 * {@code CREATED → PAYING → SUCCEEDED / FAILED / CLOSED}；
 * {@code SUCCEEDED → REFUNDING → PART_REFUNDED → REFUNDED}。</p>
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
