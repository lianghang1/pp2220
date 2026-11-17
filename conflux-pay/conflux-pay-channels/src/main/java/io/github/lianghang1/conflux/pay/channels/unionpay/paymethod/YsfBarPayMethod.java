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

import java.util.HashMap;
import java.util.Map;

/**
 * 云闪付付款码支付（orderType=unionpay，micropay）：同步扣款即时返回终态。
 * channelExtras 约定：{@code authCode}（用户付款码）必填，{@code termId} 选填。
 *
 * @author lianghang
 */
public class YsfBarPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_BAR.getCode();
    }

    @Override
    public String orderType() {
        return "unionpay";
    }

    @Override
    public boolean isBarcode() {
        return true;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                           UnionPayParams params, UnionPayClient client) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        String authCode = extras == null ? null : extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "云闪付付款码支付需在 channelExtras 中提供 authCode");
        }
        Map<String, Object> requestParams = new HashMap<String, Object>();
        requestParams.put("orderNo", payment.getBizOrderNo());
        requestParams.put("orderTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestParams.put("txnAmt", payment.getAmount());
        requestParams.put("currencyCode", "156");
        requestParams.put("orderInfo", StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        requestParams.put("orderType", orderType());
        requestParams.put("authCode", authCode);
        requestParams.put("termId", extras == null || extras.get("termId") == null
                ? "00000001" : extras.get("termId"));
        Map<String, Object> result = client.execute("付款码支付", "/gateway/api/pay/micropay",
                config, requestParams);
        boolean success = "00".equals(String.valueOf(result.get("respCode")));
        return new PaymentInvocation()
                .setChannelOrderNo(result.get("transIndex") == null
                        ? null : String.valueOf(result.get("transIndex")))
                .setPayload(PayPayload.none())
                .setState(success ? PaymentState.SUCCEEDED : PaymentState.PAYING);
    }
}
