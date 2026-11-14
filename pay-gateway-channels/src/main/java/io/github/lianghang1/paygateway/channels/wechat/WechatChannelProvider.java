package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.channel.ChannelCode;
import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.channel.ChannelProvider;
import io.github.lianghang1.paygateway.channel.PayMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 微信支付渠道提供者：普通商户模式全能力支持
 * （JSAPI/Native/H5/APP 下单、查询、关闭、退款、商家转账、分账、回调、账单），
 * 退款/转账/分账支持服务商模式的特约商户号传递。
 *
 * <p>付款码支付（WECHAT_BAR）依赖的 API 不在当前 SDK 覆盖范围内，
 * 不在支持清单内；服务商模式支付下单待后续扩展。</p>
 *
 * @author lianghang
 */
public class WechatChannelProvider implements ChannelProvider {

    private final WechatClientHolder holder = new WechatClientHolder();

    @Override
    public String channelCode() {
        return ChannelCode.WECHAT;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(PayMethod.WECHAT_JSAPI.getCode());
        methods.add(PayMethod.WECHAT_NATIVE.getCode());
        methods.add(PayMethod.WECHAT_H5.getCode());
        methods.add(PayMethod.WECHAT_APP.getCode());
        methods.add(PayMethod.WECHAT_TRANS.getCode());
        return methods;
    }

    @Override
    public ChannelParams parseParams(Map<String, String> properties) {
        return WechatParams.fromMap(properties);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.PaymentHandler paymentHandler() {
        return new WechatPaymentHandler(holder);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.RefundHandler refundHandler() {
        return new WechatRefundHandler(holder);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.TransferHandler transferHandler() {
        return new WechatTransferHandler(holder);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.ProfitShareHandler profitShareHandler() {
        return new WechatProfitShareHandler(holder);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.BillHandler billHandler() {
        return new WechatBillHandler(holder);
    }

    @Override
    public io.github.lianghang1.paygateway.channel.ChannelCallbackHandler callbackHandler() {
        return new WechatCallbackHandler(holder);
    }
}
