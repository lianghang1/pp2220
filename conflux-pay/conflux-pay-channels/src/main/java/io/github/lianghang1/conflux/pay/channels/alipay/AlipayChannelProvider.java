package io.github.lianghang1.conflux.pay.channels.alipay;

import io.github.lianghang1.conflux.pay.channel.ChannelCode;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.PayMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝渠道提供者：扫码/手机网站/电脑网站/APP/JSAPI 下单、查询、关闭、
 * 退款（同步）、单笔转账、分账（结算规则）、异步通知验签、账单对账。
 *
 * <p>服务商模式（ISV）支付下单待后续扩展；退款/转账/分账按普通商户实现。</p>
 *
 * @author lianghang
 */
public class AlipayChannelProvider implements ChannelProvider {

    private final AlipayClientHolder holder = new AlipayClientHolder();

    @Override
    public String channelCode() {
        return ChannelCode.ALIPAY;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(PayMethod.ALI_QR.getCode());
        methods.add(PayMethod.ALI_WAP.getCode());
        methods.add(PayMethod.ALI_PAGE.getCode());
        methods.add(PayMethod.ALI_APP.getCode());
        methods.add(PayMethod.ALI_JSAPI.getCode());
        methods.add(PayMethod.ALI_BAR.getCode());
        methods.add(PayMethod.ALI_TRANSFER.getCode());
        return methods;
    }

    @Override
    public ChannelParams parseParams(Map<String, String> properties) {
        return AlipayParams.fromMap(properties);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.PaymentHandler paymentHandler() {
        return new AlipayPaymentHandler();
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.RefundHandler refundHandler() {
        return new AlipayRefundHandler(holder);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.TransferHandler transferHandler() {
        return new AlipayTransferHandler(holder);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.ProfitShareHandler profitShareHandler() {
        return new AlipayProfitShareHandler(holder);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.BillHandler billHandler() {
        return new AlipayBillHandler(holder);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler callbackHandler() {
        return new AlipayCallbackHandler(holder);
    }

    @Override
    public io.github.lianghang1.conflux.pay.channel.ChannelUserHandler channelUserHandler() {
        return new AlipayChannelUserHandler(holder);
    }
}
