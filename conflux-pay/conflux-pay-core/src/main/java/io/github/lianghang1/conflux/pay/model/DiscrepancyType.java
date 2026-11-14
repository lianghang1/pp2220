package io.github.lianghang1.conflux.pay.model;

/**
 * 对账差错类型。
 *
 * <p>命名视角以本地系统为参照：{@code CHANNEL_MISSING} 表示渠道侧缺失（本地有渠道无），
 * {@code LOCAL_MISSING} 表示本地缺失（渠道有本地无）。</p>
 *
 * @author lianghang
 */
public enum DiscrepancyType {

    /** 渠道侧缺失：本地存在（成功类）订单，渠道账单中无对应流水 */
    CHANNEL_MISSING,
    /** 本地缺失：渠道账单有流水，本地无对应订单 */
    LOCAL_MISSING,
    /** 金额不一致：两侧均有流水但金额不等 */
    AMOUNT_MISMATCH,
    /** 状态不一致：两侧均有流水但成功状态不等（发现时会自动修正本地状态） */
    STATE_MISMATCH
}
