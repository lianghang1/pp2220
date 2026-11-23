package com.sfi.conflux.pay.channels.unionpay.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayClient;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayParams;
import com.sfi.conflux.pay.model.Payment;

/**
 * 云闪付转接支付宝付款码支付（orderType=alipay，micropay）：同步扣款。
 * channelExtras 约定：{@code authCode}（支付宝付款码）必填。
 *
 * @author lianghang
 */
public class AliBarPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_ALI_BAR.getCode();
    }

    @Override
    public String orderType() {
        return "alipay";
    }

    @Override
    public boolean isBarcode() {
        return true;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                           UnionPayParams params, UnionPayClient client) {
        return WxBarPayMethod.barPay(payment, config, params, client, orderType());
    }
}
