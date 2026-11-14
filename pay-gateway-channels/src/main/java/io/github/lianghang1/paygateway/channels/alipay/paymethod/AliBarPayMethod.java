package io.github.lianghang1.paygateway.channels.alipay.paymethod;

import com.alipay.api.domain.AlipayTradePayModel;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.response.AlipayTradePayResponse;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.alipay.AlipayStateTranslator;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;

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
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        AlipayTradePayRequest request = new AlipayTradePayRequest();
        request.setNotifyUrl(AlipayClientHolder.paramsOf(config).getNotifyUrl());
        AlipayTradePayModel model = new AlipayTradePayModel();
        model.setOutTradeNo(payment.getPaymentId());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
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
