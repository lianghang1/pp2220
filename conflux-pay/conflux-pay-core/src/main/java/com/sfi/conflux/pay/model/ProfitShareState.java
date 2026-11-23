package com.sfi.conflux.pay.model;

/**
 * 分账单状态机：{@code CREATED → PROCESSING → FINISHED / FAILED}。
 *
 * @author lianghang
 */
public enum ProfitShareState {

    /** 已创建，尚未发起渠道分账 */
    CREATED,
    /** 已发起渠道分账，等待渠道确认 */
    PROCESSING,
    /** 分账完成（终态） */
    FINISHED,
    /** 分账失败（终态） */
    FAILED;

    /** 是否终态。 */
    public boolean isTerminal() {
        return this == FINISHED || this == FAILED;
    }
}
