package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;

/**
 * 渠道支付能力：下单、查单、关单。
 *
 * <p>实现约定：所有方法可能被并发调用，实现必须线程安全；渠道调用失败
 * 抛 {@code GatewayException(CHANNEL_CALL_FAILED)}（保留渠道原始错误信息）；
 * 订单状态判断由引擎负责，实现方不重复校验本地状态。</p>
 *
 * @author lianghang
 */
public interface PaymentHandler {

    /**
     * 发起渠道支付。
     *
     * <p>常规方式返回 PAYING 状态与唤起参数；付款码等即时扣款方式
     * 在渠道扣款成功时直接返回 SUCCEEDED 状态（payload 为 NONE）。
     * 渠道侧单号尽力回填（部分渠道在下单阶段不返回单号）。</p>
     */
    PaymentInvocation createPayment(Payment payment, ChannelConfig config);

    /**
     * 查询渠道侧订单快照：用于补单（本地 PAYING 订单的终态确认）与对账。
     */
    PaymentSnapshot queryPayment(Payment payment, ChannelConfig config);

    /**
     * 关闭渠道订单。渠道侧已支付时抛 {@code GatewayException(STATE_ILLEGAL)}，
     * 引擎捕获后会复查渠道订单并按结果修正本地状态。
     */
    void closePayment(Payment payment, ChannelConfig config);
}
