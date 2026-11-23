package com.sfi.conflux.pay.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.alipay.AlipayClientHolder;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.alipay.AlipayStateTranslator;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.StringUtils;

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
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
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
