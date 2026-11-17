package io.github.lianghang1.conflux.pay.channels.alipay.paymethod;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
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
 * 支付宝 JSAPI 支付（小程序/生活号，alipay.trade.create）：返回 tradeNo。
 * channelExtras 约定：{@code openid}（支付宝 buyerId）必填。
 *
 * @author lianghang
 */
public class AliJsapiPayMethod implements AlipayPayMethod {

    @Override
    public String method() {
        return PayMethod.ALI_JSAPI.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, AlipayClientHolder holder) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        String buyerId = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(buyerId)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "JSAPI 支付需在 channelExtras 中提供 openid（支付宝 buyerId）");
        }
        AlipayTradeCreateRequest request = new AlipayTradeCreateRequest();
        request.setNotifyUrl(config.getNotifyUrl());
        AlipayTradeCreateModel model = new AlipayTradeCreateModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setTotalAmount(AlipayStateTranslator.toYuan(payment.getAmount()));
        model.setSubject(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        model.setBuyerId(buyerId);
        request.setBizModel(model);
        AlipayTradeCreateResponse response = holder.execute("JSAPI 下单", config,
                (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        return new PaymentInvocation().setPayload(PayPayload.jsapi(
                JsonUtils.toJson(new TradeNoPayload(response.getTradeNo()))))
                .setState(PaymentState.PAYING);
    }

    /** JSAPI 唤起参数结构（序列化为 JSON 交付使用方）。 */
    public static class TradeNoPayload {

        private final String tradeNo;

        public TradeNoPayload(String tradeNo) {
            this.tradeNo = tradeNo;
        }

        public String getTradeNo() {
            return tradeNo;
        }
    }
}
