package io.github.lianghang1.conflux.pay.channels.alipay.paymethod;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import com.alipay.api.domain.AlipayTradePayModel;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.response.AlipayTradePayResponse;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayClientHolder;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayStateTranslator;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;

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
        request.setNotifyUrl(config.getNotifyUrl());
        AlipayTradePayModel model = new AlipayTradePayModel();
        model.setOutTradeNo(payment.getPaymentId());
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
