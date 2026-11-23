package com.sfi.conflux.pay.channels.unionpay.paymethod;

import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayClient;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.unionpay.UnionPayParams;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 云闪付转接微信付款码支付（orderType=wechat，micropay）：同步扣款。
 * channelExtras 约定：{@code authCode}（微信付款码）必填，{@code termId} 选填。
 *
 * @author lianghang
 */
public class WxBarPayMethod implements UnionPayMethod {

    @Override
    public String method() {
        return PayMethod.UNION_WX_BAR.getCode();
    }

    @Override
    public String orderType() {
        return "wechat";
    }

    @Override
    public boolean isBarcode() {
        return true;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config,
                                           UnionPayParams params, UnionPayClient client) {
        return barPay(payment, config, params, client, orderType());
    }

    /** 云闪付转接付款码公共实现（微信/支付宝共用，仅 orderType 不同）。 */
    static PaymentInvocation barPay(Payment payment, ChannelConfig config,
                                    UnionPayParams params, UnionPayClient client, String orderType) {
        Map<String, String> extras = payment.getExtParams() == null
                ? new HashMap<String, String>() : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
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
        requestParams.put("orderType", orderType);
        requestParams.put("authCode", extras.get("authCode"));
        requestParams.put("termId", extras.get("termId") == null ? "00000001" : extras.get("termId"));
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
