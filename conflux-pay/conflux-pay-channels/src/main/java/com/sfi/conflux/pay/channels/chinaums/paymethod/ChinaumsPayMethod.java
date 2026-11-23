package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channels.chinaums.ChinaumsClient;
import com.sfi.conflux.pay.model.Payment;

/**
 * 天满支付方式契约：一种支付方式一个实现类，由渠道门面按 method 路由。
 *
 * @author lianghang
 */
public interface ChinaumsPayMethod {

    /** 支付方式编码（PayMethod.code）。 */
    String method();

    /** 发起渠道支付。 */
    PaymentInvocation createPayment(Payment payment, ChannelConfig config, ChinaumsClient client);
}
