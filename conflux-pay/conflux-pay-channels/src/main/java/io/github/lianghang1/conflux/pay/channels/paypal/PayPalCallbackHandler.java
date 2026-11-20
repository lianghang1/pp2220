package io.github.lianghang1.conflux.pay.channels.paypal;

import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.BizOrderType;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * PayPal Webhook 处理器：事件 JSON → verify-webhook-signature 服务端验签
 * → 按事件类型归类（PAYMENT.CAPTURE.COMPLETED / PAYMENT.CAPTURE.REFUNDED）。
 *
 * <p>验签调用 PayPal 官方验签接口（transmission 头 + webhookId），
 * 配置未提供 webhookId 时跳过验签（仅建议测试环境）。事件体经
 * {@code resource.custom_id}（下单时固化的网关支付单号）定位订单。</p>
 *
 * @author lianghang
 */
public class PayPalCallbackHandler implements ChannelCallbackHandler {

    private final PayPalClient client;

    public PayPalCallbackHandler(PayPalClient client) {
        this.client = client;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, ChannelConfig config) {
        Map<String, Object> event = JsonUtils.toMap(raw.getBody());
        String clientId = raw.getQueryParams() == null ? null : raw.getQueryParams().get("clientId");
        PayPalParams params = PayPalClient.paramsOf(config);
        String eventType = PayPalTranslator.text(event.get("event_type"));

        if (StringUtils.isNotBlank(params.getWebhookId())) {
            verifyWebhook(raw, config, params, clientId);
        }

        CallbackEvent callbackEvent = new CallbackEvent()
                .setConfigId(config.getConfigId())
                .setRaw(toFlatTextMap(event));
        if (eventType != null && eventType.contains("REFUND")) {
            callbackEvent.setType(BizOrderType.REFUND)
                    .setBizOrderNo(customId(event))
                    .setChannelOrderNo(resourceId(event))
                    .setSuccess("COMPLETED".equals(PayPalTranslator.text(
                            resourceOf(event, "status"))));
            return callbackEvent;
        }
        boolean captured = "PAYMENT.CAPTURE.COMPLETED".equals(eventType)
                || "CHECKOUT.ORDER.COMPLETED".equals(eventType);
        callbackEvent.setType(BizOrderType.PAYMENT)
                .setBizOrderNo(customId(event))
                .setChannelOrderNo(resourceId(event))
                .setSuccess(captured)
                .setAmount(payAmount(event));
        return callbackEvent;
    }

    /** 官方验签接口校验 Webhook 签名；失败抛签名异常。 */
    private void verifyWebhook(ChannelCallbackRequest raw, ChannelConfig config,
                               PayPalParams params, String clientId) {
        Map<String, String> headers = raw.getHeaders() == null ? new HashMap<String, String>() : raw.getHeaders();
        Map<String, Object> verifyBody = new HashMap<String, Object>();
        verifyBody.put("transmission_id", headers.get("Paypal-Transmission-Id"));
        verifyBody.put("transmission_time", headers.get("Paypal-Transmission-Time"));
        verifyBody.put("cert_url", headers.get("Paypal-Cert-Url"));
        verifyBody.put("auth_algo", headers.get("Paypal-Auth-Algo"));
        verifyBody.put("transmission_sig", headers.get("Paypal-Transmission-Sig"));
        verifyBody.put("webhook_id", params.getWebhookId());
        verifyBody.put("webhook_event", JsonUtils.fromJson(raw.getBody(), Map.class));
        Map<String, Object> result = client.execute("Webhook 验签", config, "POST",
                "/v1/notifications/verify-webhook-signature", verifyBody);
        if (!"SUCCESS".equals(PayPalTranslator.text(result.get("verification_status")))) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "PayPal Webhook 验签未通过");
        }
    }

    /** 事件体 resource 中的网关单号（下单固化的 custom_id）。 */
    private String customId(Map<String, Object> event) {
        return PayPalTranslator.text(resourceOf(event, "custom_id"));
    }

    /** 事件体 resource 的资源 ID（capture_id/refund_id 等）。 */
    private String resourceId(Map<String, Object> event) {
        return PayPalTranslator.text(resourceOf(event, "id"));
    }

    /** 事件体 resource 的金额（分）。 */
    private Long payAmount(Map<String, Object> event) {
        Map<String, Object> resource = resource(event);
        if (resource == null) {
            return null;
        }
        Object amount = resource.get("amount");
        if (amount instanceof Map) {
            return PayPalTranslator.toFen(((Map<?, ?>) amount).get("value") == null
                    ? null : String.valueOf(((Map<?, ?>) amount).get("value")));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resource(Map<String, Object> event) {
        Object resource = event.get("resource");
        return resource instanceof Map ? (Map<String, Object>) resource : null;
    }

    private Object resourceOf(Map<String, Object> event, String field) {
        Map<String, Object> resource = resource(event);
        return resource == null ? null : resource.get(field);
    }

    private Map<String, String> toFlatTextMap(Map<String, Object> source) {
        Map<String, String> flat = new HashMap<String, String>();
        flat.put("event_type", PayPalTranslator.text(source.get("event_type")));
        flat.put("id", PayPalTranslator.text(source.get("id")));
        return flat;
    }

    @Override
    public String successReply() {
        return "OK";
    }

    @Override
    public String failureReply() {
        return "FAIL";
    }
}
