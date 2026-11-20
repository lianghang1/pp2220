package io.github.lianghang1.conflux.pay.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayStateTranslator;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.StringUtils;

/**
 * 支付宝 APP 支付（alipay.trade.app.pay）：返回 orderStr（APP payload，
 * 交由支付宝客户端 SDK 唤起）。
 *
 * @author lianghang
 */
public class AliAppPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_APP.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);
        String body = holder.execute("APP 下单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        }).getBody();
        return new PaymentInvocation().setPayload(PayPayload.app(body)).setState(PaymentState.PAYING);
    }
}
