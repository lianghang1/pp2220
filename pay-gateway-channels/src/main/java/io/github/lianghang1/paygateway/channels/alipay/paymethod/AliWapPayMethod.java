package io.github.lianghang1.paygateway.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.alipay.AlipayStateTranslator;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 支付宝手机网站支付（alipay.trade.wap.pay）：返回自动提交表单（REDIRECT）。
 * ISV 模式（appAuthToken 已配置）经 putOtherTextParam 携带授权令牌。
 *
 * @author lianghang
 */
public class AliWapPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_WAP.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(AlipayClientHolder.paramsOf(config).getNotifyUrl());
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(payment.getPaymentId());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        model.setProductCode("QUICK_WAP_WAY");
        request.setBizModel(model);
        String body = holder.execute("H5 下单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.pageExecute(request);
        }).getBody();
        return new PaymentInvocation().setPayload(PayPayload.redirect(body)).setState(PaymentState.PAYING);
    }
}
