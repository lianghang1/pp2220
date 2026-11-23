package com.sfi.conflux.pay.channels.unionpay.paymethod;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayClient;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayParams;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

import java.util.Map;

/**
 * 云闪付转接微信 JSAPI 支付（orderType=wechatJs，统一下单）：
 * 返回微信唤起参数（JSAPI payload）。channelExtras 约定：{@code openid} 必填。
 *
 * @author lianghang
 */
public class WxJsapiPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_WX_JSAPI.getCode();
    }

    @Override
    public String orderType() {
        return "wechatJs";
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
        String openid = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(openid)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "云闪付转接微信 JSAPI 需在 channelExtras 中提供 openid");
        }
        Map<String, Object> result = client.execute("统一下单", "/gateway/api/pay/unifiedorder",
                config, YsfQrPayMethod.buildUnifiedParams(payment, params, config, openid));
        String payData = result.get("payData") == null ? null : String.valueOf(result.get("payData"));
        return new PaymentInvocation().setPayload(PayPayload.jsapi(payData)).setState(PaymentState.PAYING);
    }
}
