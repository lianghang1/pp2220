package io.github.lianghang1.conflux.pay.channels.unionpay;

import io.github.lianghang1.conflux.pay.channel.ChannelCode;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.PayMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 云闪付渠道提供者：银联二维码（跳转）与银联付款码下单、查单、关单、退款、
 * 回调验签。分账与账单能力依赖银联开放平台对应产品的开通情况，未在支持清单内。
 *
 * <p>商户 P12 证书与平台公钥由使用方在渠道参数中提供；
 * 支持普通商户直连与服务商模式（subMchNo 承载特约商户号）。</p>
 *
 * @author lianghang
 */
public class UnionPayChannelProvider implements ChannelProvider {

    private final UnionPayClient client = new UnionPayClient();

    @Override
    public String channelCode() {
        return ChannelCode.UNIONPAY;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(PayMethod.UNION_QR.getCode());
        methods.add(PayMethod.UNION_BAR.getCode());
        methods.add(PayMethod.UNION_WX_JSAPI.getCode());
        methods.add(PayMethod.UNION_WX_BAR.getCode());
        methods.add(PayMethod.UNION_ALI_JSAPI.getCode());
        methods.add(PayMethod.UNION_ALI_BAR.getCode());
        return methods;
    }

    @Override
    public ChannelParams parseParams(Map<String, String> properties) {
        return UnionPayParams.fromMap(properties);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.PaymentHandler paymentHandler() {
        return new UnionPayPaymentHandler();
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.RefundHandler refundHandler() {
        return new UnionPayRefundHandler(client);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler callbackHandler() {
        return new UnionPayCallbackHandler(client);
    }
}
