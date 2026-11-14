package io.github.lianghang1.conflux.pay.channels.unionpay.paymethod;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayClient;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayParams;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.Map;

/**
 * 云闪付转接支付宝 JSAPI 支付（orderType=alipayJs，统一下单）：
 * 返回支付宝唤起参数。channelExtras 约定：{@code openid}（支付宝 buyerUserId）必填。
 *
 * @author lianghang
 */
public class AliJsapiPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_ALI_JSAPI.getCode();
    }

    @Override
    public String orderType() {
        return "alipayJs";
    }

    @Override
    public boolean isBarcode() {
        return false;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                           UnionPayParams params, UnionPayClient client) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        String userId = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(userId)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "云闪付转接支付宝 JSAPI 需在 channelExtras 中提供 openid（buyerUserId）");
        }
        Map<String, Object> result = client.execute("统一下单", "/gateway/api/pay/unifiedorder",
                config, YsfQrPayMethod.buildUnifiedParams(payment, params, config, userId));
        String payData = result.get("payData") == null ? null : String.valueOf(result.get("payData"));
        return new PaymentInvocation().setPayload(PayPayload.jsapi(payData)).setState(PaymentState.PAYING);
    }
}
