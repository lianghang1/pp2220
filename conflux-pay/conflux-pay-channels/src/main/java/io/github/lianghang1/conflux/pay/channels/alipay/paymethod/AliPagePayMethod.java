package io.github.lianghang1.conflux.pay.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
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
 * 支付宝电脑网站支付（alipay.trade.page.pay）：返回自动提交表单（REDIRECT）。
 *
 * @author lianghang
 */
public class AliPagePayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_PAGE.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(config.getNotifyUrl());
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(payment.getPaymentId());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);
        String body = holder.execute("电脑网站下单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.pageExecute(request);
        }).getBody();
        return new PaymentInvocation().setPayload(PayPayload.redirect(body)).setState(PaymentState.PAYING);
    }
}
