package io.github.lianghang1.paygateway.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.paygateway.channels.alipay.AlipayStateTranslator;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 支付宝 APP 支付（alipay.trade.app.pay）：返回 orderStr（APP payload，
 * 交由支付宝客户端 SDK 唤起）。
 *
 * @author lianghang
 */
public class AliAppPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return "ALI_APP";
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        request.setNotifyUrl(AlipayClientHolder.paramsOf(config).getNotifyUrl());
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(payment.getPaymentId());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);
        String body = holder.execute("APP 下单", config, (client, authToken) -> client.execute(request, authToken)).getBody();
        return new PaymentInvocation().setPayload(PayPayload.app(body)).setState(PaymentState.PAYING);
    }
}
