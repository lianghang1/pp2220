package io.github.lianghang1.paygateway.channels.unionpay.paymethod;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayClient;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayParams;
import io.github.lianghang1.paygateway.model.Payment;

/**
 * 云闪付转接支付宝付款码支付（orderType=alipay，micropay）：同步扣款。
 * channelExtras 约定：{@code authCode}（支付宝付款码）必填。
 *
 * @author lianghang
 */
public class AliBarPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return "UNION_ALI_BAR";
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
