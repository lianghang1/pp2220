package io.github.lianghang1.conflux.pay.channels.paypal.paymethod;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channels.paypal.PayPalClient;
import io.github.lianghang1.conflux.pay.model.Payment;

/**
 * PayPal 支付方式契约：一种支付方式一个实现类，由渠道入口按 wayCode 路由。
 *
 * <p>查单/捕获与支付方式无关（按 PayPal 订单号），由渠道门面直接处理。</p>
 *
 * @author lianghang
 */
public interface PayPalPayMethod {

    /** 支付方式编码（PayMethod.code）。 */
    String method();

    /** 发起渠道支付。 */
    PaymentInvocation createPayment(Payment payment, ChannelConfig config, PayPalClient client);
}
