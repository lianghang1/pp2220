package io.github.lianghang1.conflux.pay.channels.chinaums;

import io.github.lianghang1.conflux.pay.channel.ChannelCode;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentHandler;
import io.github.lianghang1.conflux.pay.channel.RefundHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 银联商务天满渠道提供者：多种支付方式下单（微信/支付宝/云闪付 × APP/H5/小程序/公众号
 * + C扫B主扫）、查单、退款、退款查询、关单、回调（主动查单确认）。
 *
 * <p>认证采用 OPEN-BODY-SIG，无状态每请求签名；分账仅提供异步分账确认端点，
 * 与本项目两段式分账语义不完全一致，未暴露 ProfitShareHandler；
 * B扫C被扫走独立 poslink 协议，未纳入本渠道。</p>
 *
 * @author lianghang
 */
public class ChinaumsChannelProvider implements ChannelProvider {

    private final ChinaumsClient client = new ChinaumsClient();

    @Override
    public String channelCode() {
        return ChannelCode.CHINAUMS;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(PayMethod.CHINAUMS_WX_APP.getCode());
        methods.add(PayMethod.CHINAUMS_WX_H5.getCode());
        methods.add(PayMethod.CHINAUMS_WX_MINI.getCode());
        methods.add(PayMethod.CHINAUMS_WX_JSAPI.getCode());
        methods.add(PayMethod.CHINAUMS_ALI_APP.getCode());
        methods.add(PayMethod.CHINAUMS_ALI_H5.getCode());
        methods.add(PayMethod.CHINAUMS_ALI_MINI.getCode());
        methods.add(PayMethod.CHINAUMS_ALI_JSAPI.getCode());
        methods.add(PayMethod.CHINAUMS_UAC_APP.getCode());
        methods.add(PayMethod.CHINAUMS_UAC_H5.getCode());
        methods.add(PayMethod.CHINAUMS_UAC_MINI.getCode());
        methods.add(PayMethod.CHINAUMS_UAC_JSAPI.getCode());
        methods.add(PayMethod.CHINAUMS_QR.getCode());
        return methods;
    }

    @Override
    public ChannelConfig parseParams(Map<String, String> properties) {
        return ChinaumsParams.fromMap(properties);
    }

    @Override
    public PaymentHandler paymentHandler() {
        return new ChinaumsPaymentHandler();
    }

    @Override
    public RefundHandler refundHandler() {
        return new ChinaumsRefundHandler(client);
    }

    @Override
    public ChannelCallbackHandler callbackHandler() {
        return new ChinaumsCallbackHandler(client);
    }
}
