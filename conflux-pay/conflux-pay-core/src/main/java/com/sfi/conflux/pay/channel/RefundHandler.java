package com.sfi.conflux.pay.channel;

import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.Refund;

/**
 * 渠道退款能力：发起退款、查询退款。
 *
 * <p>实现约定与 {@link PaymentHandler} 一致；退款金额、原支付单渠道上下文
 * 由入参携带，实现方不重复校验可退余额（引擎已校验）。</p>
 *
 * @author lianghang
 */
public interface RefundHandler {

    /**
     * 发起渠道退款；返回渠道退款单号与当前状态
     * （同步返回结果的渠道直接给出 SUCCEEDED/FAILED，异步渠道给出 PROCESSING）。
     */
    RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config);

    /** 查询渠道侧退款状态：用于退款单补单与对账。 */
    RefundSnapshot queryRefund(Refund refund, ChannelConfig config);
}
