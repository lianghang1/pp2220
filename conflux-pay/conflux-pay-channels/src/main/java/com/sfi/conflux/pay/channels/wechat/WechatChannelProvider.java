package com.sfi.conflux.pay.channels.wechat;

import com.sfi.conflux.pay.channel.ChannelCode;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelProvider;
import com.sfi.conflux.pay.channel.PayMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 微信支付渠道提供者：V2/V3 双协议，支付方式经独立 PayMethod 类承载
 * （JSAPI 公众号、Lite 小程序、Native 扫码、H5、APP、Bar 付款码），
 * 退款/转账（V3 商家转账 + V2 企业付款）/分账/账单/通知全能力。
 *
 * <p>使用方在渠道参数中指定 wxApiVersion（v2/v3，缺省 v3）；
 * 服务商模式经 subMchNo 透传 WxJava。</p>
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
        methods.add(PayMethod.WECHAT_LITE.getCode());
        methods.add(PayMethod.WECHAT_NATIVE.getCode());
        methods.add(PayMethod.WECHAT_H5.getCode());
        methods.add(PayMethod.WECHAT_APP.getCode());
        methods.add(PayMethod.WECHAT_BAR.getCode());
        methods.add(PayMethod.WECHAT_TRANS.getCode());
        return methods;
    }

    @Override
    public ChannelConfig parseParams(Map<String, String> properties) {
        return WechatParams.fromMap(properties);
    }

    @Override
    public com.sfi.conflux.pay.channel.PaymentHandler paymentHandler() {
        return new WechatPaymentHandler();
    }

    @Override
    public com.sfi.conflux.pay.channel.RefundHandler refundHandler() {
        return new WechatRefundHandler(holder);
    }

    @Override
    public com.sfi.conflux.pay.channel.TransferHandler transferHandler() {
        return new WechatTransferHandler(holder);
    }

    @Override
    public com.sfi.conflux.pay.channel.ProfitShareHandler profitShareHandler() {
        return new WechatProfitShareHandler(holder);
    }

    @Override
    public com.sfi.conflux.pay.channel.BillHandler billHandler() {
        return new WechatBillHandler(holder);
    }

    @Override
    public com.sfi.conflux.pay.channel.ChannelCallbackHandler callbackHandler() {
        return new WechatCallbackHandler(holder);
    }

    @Override
    public com.sfi.conflux.pay.channel.ChannelUserHandler channelUserHandler() {
        return new WechatChannelUserHandler();
    }
}
