package com.sfi.conflux.pay.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
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
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
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
