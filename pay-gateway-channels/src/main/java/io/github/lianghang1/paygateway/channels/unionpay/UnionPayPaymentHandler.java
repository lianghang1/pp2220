package io.github.lianghang1.paygateway.channels.unionpay;

import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 云闪付支付处理器：银联二维码（跳转支付）与银联付款码（即时扣款）。
 *
 * <p>统一下单返回 {@code payData}——银联二维码为支付跳转地址（REDIRECT），
 * 微信/支付宝 JSAPI 经云闪付转接的 payData 为前端唤起参数 JSON（JSAPI）；
 * 付款码支付（micropay）同步扣款，channelExtras 约定 {@code authCode}。</p>
 *
 * @author lianghang
 */
public class UnionPayPaymentHandler implements PaymentHandler {

    private final UnionPayClient client;

    public UnionPayPaymentHandler(UnionPayClient client) {
        this.client = client;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        String merchantId = resolveMerchantId(config, params);
        Map<String, Object> requestParams = new HashMap<String, Object>();
        UnionPayTranslator.fillPublicParams(requestParams, payment.getPaymentId(), payment.getAmount(),
                payment.getSubject(), merchantId, params.getSerProvId());
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : flatExtras(payment.getExtParams());

        boolean barcode = "UNION_BAR".equals(payment.getMethod());
        requestParams.put("orderType", barcode ? "unionpay" : "upJs");
        if (barcode) {
            String authCode = extras == null ? null : extras.get("authCode");
            if (StringUtils.isBlank(authCode)) {
                throw new GatewayException(GatewayErrorCode.BAD_REQUEST,
                        "云闪付付款码支付需在 channelExtras 中提供 authCode");
            }
            requestParams.put("authCode", authCode);
            requestParams.put("termId", extras == null || extras.get("termId") == null
                    ? "00000001" : extras.get("termId"));
        } else {
            requestParams.put("backUrl", params.getNotifyUrl());
            requestParams.put("frontUrl", params.getNotifyUrl());
            requestParams.put("customerIp", StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
        }

        String apiUri = barcode ? "/gateway/api/pay/micropay" : "/gateway/api/pay/unifiedorder";
        Map<String, Object> result = client.execute(barcode ? "付款码支付" : "统一下单",
                apiUri, config, requestParams);
        String respCode = String.valueOf(result.get("respCode"));

        if (barcode) {
            return new PaymentInvocation()
                    .setChannelOrderNo(text(result.get("transIndex")))
                    .setPayload(PayPayload.none())
                    .setState("00".equals(respCode) ? PaymentState.SUCCEEDED : PaymentState.PAYING);
        }
        String payData = text(result.get("payData"));
        PayPayload payload = payData != null && payData.trim().startsWith("{")
                ? PayPayload.jsapi(payData) : PayPayload.redirect(payData);
        return new PaymentInvocation().setPayload(payload).setState(PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        Map<String, Object> requestParams = new HashMap<String, Object>();
        UnionPayTranslator.fillPublicParams(requestParams, payment.getPaymentId(), payment.getAmount(),
                payment.getSubject(), resolveMerchantId(config, params), params.getSerProvId());
        Map<String, Object> result = client.execute("查单", "/gateway/api/pay/queryOrder", config, requestParams);
        String origRespCode = text(result.get("origRespCode"));
        PaymentState state = UnionPayTranslator.fromOrigRespCode(origRespCode);
        return new PaymentSnapshot()
                .setChannelOrderNo(text(result.get("transIndex")))
                .setState(state)
                .setRawMsg(origRespCode);
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        Map<String, Object> requestParams = new HashMap<String, Object>();
        UnionPayTranslator.fillPublicParams(requestParams, payment.getPaymentId(), payment.getAmount(),
                payment.getSubject(), resolveMerchantId(config, params), params.getSerProvId());
        client.execute("关单", "/gateway/api/pay/closeOrder", config, requestParams);
    }

    /** 特约商户号优先（服务商模式），缺省回落到直连商户号。 */
    private String resolveMerchantId(ChannelConfig config, UnionPayParams params) {
        return StringUtils.isNotBlank(config.getSubMchNo()) ? config.getSubMchNo() : params.getMerchantId();
    }

    private Map<String, String> flatExtras(String extParams) {
        Map<String, String> result = new HashMap<String, String>();
        Map<String, Object> source = JsonUtils.toMap(extParams);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
