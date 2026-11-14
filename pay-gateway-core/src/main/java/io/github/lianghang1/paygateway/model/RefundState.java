package io.github.lianghang1.paygateway.model;

/**
 * 退款单状态机。
 *
 * <p>迁移约束：{@code CREATED → PROCESSING → SUCCEEDED / FAILED / CLOSED}；
 * 渠道同步返回结果的场景允许 {@code CREATED → SUCCEEDED / FAILED}。</p>
 *
 * @author lianghang
 */
public enum RefundState {

    /** 已创建，尚未发起渠道退款 */
    CREATED,
    /** 已发起渠道退款，等待渠道确认 */
    PROCESSING,
    /** 退款成功（终态） */
    SUCCEEDED,
    /** 退款失败（终态，可重新发起） */
    FAILED,
    /** 已关闭（终态） */
    CLOSED;

    /** 是否终态。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CLOSED;
    }
}
