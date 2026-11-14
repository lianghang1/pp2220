package io.github.lianghang1.conflux.pay.channels.paypal;

import io.github.lianghang1.conflux.pay.channel.ChannelCode;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.PayMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * PayPal 渠道提供者：Orders v2 支付（下单 → 批准 → 捕获）、捕获单退款、
 * Payouts 转账、Webhook 回调验签。分账与账单能力未在支持清单内。
 *
 * <p>金额币种跟随订单（PayPal 为多币种通道）；回调地址需携带 clientId
 * 查询参数以定位渠道配置。</p>
 *
 * @author lianghang
 */
public class PayPalChannelProvider implements ChannelProvider {

    private final PayPalClient client = new PayPalClient();

    @Override
    public String channelCode() {
        return ChannelCode.PAYPAL;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(PayMethod.PAYPAL_SALE.getCode());
        methods.add(PayMethod.PAYPAL_PAYOUT.getCode());
        return methods;
    }

    @Override
    public ChannelParams parseParams(Map<String, String> properties) {
        return PayPalParams.fromMap(properties);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.PaymentHandler paymentHandler() {
        return new PayPalPaymentHandler();
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.RefundHandler refundHandler() {
        return new PayPalRefundHandler(client);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.TransferHandler transferHandler() {
        return new PayPalTransferHandler(client);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler callbackHandler() {
        return new PayPalCallbackHandler(client);
    }
}
