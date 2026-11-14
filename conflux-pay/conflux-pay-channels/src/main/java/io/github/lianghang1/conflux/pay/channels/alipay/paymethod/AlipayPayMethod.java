package io.github.lianghang1.conflux.pay.channels.alipay.paymethod;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.conflux.pay.model.Payment;

/**
 * 支付宝支付方式契约：一种支付方式一个实现类，由渠道入口按 wayCode 路由。
 *
 * <p>查单/关单与支付方式无关（统一按商户订单号），由渠道门面直接处理；
 * 实现类只负责各自的下单协议。实现要求：无状态、线程安全。</p>
 *
 * @author lianghang
 */
public interface AlipayPayMethod {

    /** 支付方式编码（PayMethod.code）。 */
    String method();

    /** 发起渠道支付。 */
    PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder);
}
