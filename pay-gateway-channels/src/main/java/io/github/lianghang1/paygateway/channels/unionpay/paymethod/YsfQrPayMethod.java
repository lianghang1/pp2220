package io.github.lianghang1.paygateway.channels.unionpay.paymethod;

import io.github.lianghang1.paygateway.api.PayPayload;
import java.util.Map;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayClient;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.UnionPayParams;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 云闪付二维码支付（orderType=upJs，统一下单）：返回支付跳转地址或唤起参数。
 *
 * @author lianghang
 */
public class YsfQrPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_QR.getCode();
    }

    @Override
    public String orderType() {
        return "upJs";
    }

    @Override
    public boolean isBarcode() {
        return false;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                           UnionPayParams params, UnionPayClient client) {
        Map<String, Object> result = client.execute("统一下单", "/gateway/api/pay/unifiedorder",
                config, buildUnifiedParams(payment, params, config, null));
        String payData = result.get("payData") == null ? null : String.valueOf(result.get("payData"));
        PayPayload payload = payData != null && payData.trim().startsWith("{")
                ? PayPayload.jsapi(payData) : PayPayload.redirect(payData);
        return new PaymentInvocation().setPayload(payload).setState(PaymentState.PAYING);
    }

    /** 统一下单公共参数组装（订单号/时间/金额/币种/描述/商户/通知与前台地址/终端信息）。 */
    static Map<String, Object> buildUnifiedParams(Payment payment, UnionPayParams params,
                                                  ChannelConfig config, String userId) {
        Map<String, Object> requestParams = new java.util.HashMap<String, Object>();
        requestParams.put("orderNo", payment.getPaymentId());
        requestParams.put("orderTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestParams.put("txnAmt", payment.getAmount());
        requestParams.put("currencyCode", "156");
        requestParams.put("orderInfo", StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        requestParams.put("orderType", "upJs");
        requestParams.put("backUrl", params.getNotifyUrl());
        requestParams.put("frontUrl", params.getNotifyUrl());
        requestParams.put("customerIp", StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
        requestParams.put("termInfo", "{\"ip\": \""
                + StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1") + "\"}");
        if (StringUtils.isNotBlank(userId)) {
            requestParams.put("userId", userId);
        }
        return requestParams;
    }
}
