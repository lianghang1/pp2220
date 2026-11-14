package io.github.lianghang1.paygateway.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
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
 * 支付宝扫码支付（当面付预下单，alipay.trade.precreate）：返回二维码码值。
 * 支付宝「订单码」与扫码同接口，由本方式覆盖。
 *
 * @author lianghang
 */
public class AliQrPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_QR.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(config.getNotifyUrl());
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(payment.getPaymentId());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        request.setBizModel(model);
        AlipayTradePrecreateResponse response =
                holder.execute("扫码下单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        return new PaymentInvocation().setPayload(PayPayload.qrCode(response.getQrCode()))
                .setState(PaymentState.PAYING);
    }
}
