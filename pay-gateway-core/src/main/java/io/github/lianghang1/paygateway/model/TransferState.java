package io.github.lianghang1.paygateway.model;

/**
 * 转账单状态机：{@code CREATED → PROCESSING → SUCCEEDED / FAILED}。
 *
 * @author lianghang
 */
public enum TransferState {

    /** 已创建，尚未发起渠道转账 */
    CREATED,
    /** 已发起渠道转账，等待渠道确认 */
    PROCESSING,
    /** 转账成功（终态） */
    SUCCEEDED,
    /** 转账失败（终态） */
    FAILED;

    /** 是否终态。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
