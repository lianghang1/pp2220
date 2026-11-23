package com.sfi.conflux.pay.channels.alipay.paymethod;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.alipay.api.domain.AlipayTradePayModel;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.response.AlipayTradePayResponse;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.alipay.AlipayClientHolder;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.alipay.AlipayStateTranslator;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

import java.util.Map;

/**
 * 支付宝付款码支付（alipay.trade.pay，scene=bar_code）：同步扣款即时返回终态。
 * channelExtras 约定：{@code authCode}（用户付款码）必填。
 *
 * @author lianghang
 */
public class AliBarPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_BAR.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        String authCode = extras == null ? null : extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        AlipayTradePayRequest request = new AlipayTradePayRequest();
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
        AlipayTradePayModel model = new AlipayTradePayModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        model.setAuthCode(authCode);
        model.setScene("bar_code");
        request.setBizModel(model);
        AlipayTradePayResponse response = holder.execute("付款码支付", config,
                (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        // 业务受理成功即扣款完成（失败在 execute 内以渠道异常抛出）
        return new PaymentInvocation()
                .setChannelOrderNo(response.getTradeNo())
                .setPayload(PayPayload.none())
                .setState(PaymentState.SUCCEEDED);
    }
}
