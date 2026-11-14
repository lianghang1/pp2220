package io.github.lianghang1.paygateway.channels.unionpay.paymethod;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayClient;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayParams;
import io.github.lianghang1.paygateway.model.Payment;

/**
 * 云闪付支付方式契约：一种支付方式一个实现类，由渠道入口按 wayCode 路由。
 *
 * <p>查单/关单与支付方式无关（统一按商户订单号），由渠道门面直接处理。
 * 实现要求：无状态、线程安全。</p>
 *
 * @author lianghang
 */
public interface UnionPayMethod {

    /** 支付方式编码（PayMethod.code）。 */
    String method();

    /** 云闪付下单的订单类型（orderType）：upJs/wechatJs/alipayJs 面向统一下单，unionpay/wechat/alipay 面向付款码。 */
    String orderType();

    /** 是否付款码（即时扣款，走 micropay 接口）。 */
    boolean isBarcode();

    /** 发起渠道支付。 */
    PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                    UnionPayParams params, UnionPayClient client);
}
