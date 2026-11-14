package io.github.lianghang1.paygateway.channels.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import io.github.lianghang1.paygateway.api.CallbackEvent;
import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;
import io.github.lianghang1.paygateway.channel.CallbackConfigLookup;
import io.github.lianghang1.paygateway.channel.ChannelCallbackHandler;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.BizOrderType;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 微信回调处理器：验签（平台证书，按 Wechatpay-Serial 头匹配）→ AES-GCM 解密 resource
 * → 按报文字段归类为支付/退款/转账/分账事件。
 *
 * <p>验签在无法预知商户参数时进行，约定使用方在回调地址携带 {@code appId} 查询参数
 * （如 {@code /callback/wechat?appId=app-001}）完成配置反查；平台证书验签只依赖
 * 证书序列号与签名，与商户参数解耦。应答报文为微信认可的 JSON 结构。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatCallbackHandler implements ChannelCallbackHandler {

    private final WechatClientHolder holder;

    public WechatCallbackHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup) {
        Map<String, String> headers = raw.getHeaders() == null ? new HashMap<String, String>() : raw.getHeaders();
        String timestamp = headers.get("Wechatpay-Timestamp");
        String nonce = headers.get("Wechatpay-Nonce");
        String signature = headers.get("Wechatpay-Signature");
        String serial = headers.get("Wechatpay-Serial");
        if (StringUtils.isBlank(timestamp) || StringUtils.isBlank(nonce)
                || StringUtils.isBlank(signature) || StringUtils.isBlank(serial)) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "缺少微信回调签名头");
        }
        String appId = raw.getQueryParams() == null ? null : raw.getQueryParams().get("appId");
        if (StringUtils.isBlank(appId)) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信回调地址需携带 appId 查询参数以定位渠道配置");
        }
        ChannelConfig config = configLookup.byChannelApp(appId);

        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .timestamp(timestamp)
                .nonce(nonce)
                .signature(signature)
                .body(raw.getBody())
                .build();
        NotificationParser parser = holder.notificationParser(config);
        WechatCallbackPayload payload = wrap(parser, requestParam);

        CallbackEvent event = new CallbackEvent()
                .setAppId(config.getAppId())
                .setRaw(flatten(JsonUtils.toMap(JsonUtils.toJson(payload))));
        applyPayloadToEvent(event, payload);
        return event;
    }

    /** 依据解密报文的字段构成归类事件类型并填充归一化字段。 */
    private void applyPayloadToEvent(CallbackEvent event, WechatCallbackPayload payload) {
        if (StringUtils.isNotBlank(payload.getOutRefundNo())) {
            event.setType(BizOrderType.REFUND)
                    .setBizOrderNo(payload.getOutRefundNo())
                    .setChannelOrderNo(payload.getRefundId())
                    .setSuccess("SUCCESS".equals(payload.getRefundStatus()))
                    .setAmount(payload.getAmount() == null ? null : payload.getAmount().getRefund())
                    .setOccurTime(WechatStateTranslator.toLocalDateTime(payload.getSuccessTime()));
            return;
        }
        if (StringUtils.isNotBlank(payload.getOutDetailNo()) || StringUtils.isNotBlank(payload.getOutBatchNo())) {
            event.setType(BizOrderType.TRANSFER)
                    .setBizOrderNo(StringUtils.isNotBlank(payload.getOutDetailNo())
                            ? payload.getOutDetailNo() : payload.getOutBatchNo())
                    .setChannelOrderNo(StringUtils.isNotBlank(payload.getDetailId())
                            ? payload.getDetailId() : payload.getBatchId())
                    .setSuccess("SUCCESS".equals(payload.getState()))
                    .setOccurTime(WechatStateTranslator.toLocalDateTime(payload.getSuccessTime()));
            return;
        }
        if (StringUtils.isNotBlank(payload.getOutOrderNo())) {
            event.setType(BizOrderType.PROFIT_SHARE)
                    .setBizOrderNo(payload.getOutOrderNo())
                    .setChannelOrderNo(payload.getOrderId())
                    .setSuccess("FINISHED".equals(payload.getState()))
                    .setOccurTime(WechatStateTranslator.toLocalDateTime(payload.getSuccessTime()));
            return;
        }
        event.setType(BizOrderType.PAYMENT)
                .setBizOrderNo(payload.getOutTradeNo())
                .setChannelOrderNo(payload.getTransactionId())
                .setSuccess("SUCCESS".equals(payload.getTradeState()))
                .setAmount(payload.getAmount() == null ? null : payload.getAmount().getTotal())
                .setOccurTime(WechatStateTranslator.toLocalDateTime(payload.getSuccessTime()));
    }

    /** 平台证书验签 + resource 解密；验签失败/解密失败统一按验签异常上抛。 */
    private WechatCallbackPayload wrap(NotificationParser parser, RequestParam requestParam) {
        try {
            return parser.parse(requestParam, WechatCallbackPayload.class);
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "微信回调验签/解析失败: {}", e.getMessage(), e);
        }
    }

    /** 解密报文字段平铺为原始字段留痕（嵌套对象取一层键值）。 */
    private Map<String, String> flatten(Map<String, Object> source) {
        Map<String, String> flat = new HashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() instanceof Map) {
                JsonNode node = JsonUtils.readTree(JsonUtils.toJson(entry.getValue()));
                if (node != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        flat.put(entry.getKey() + "." + field.getKey(),
                                field.getValue().isValueNode() ? field.getValue().asText() : field.getValue().toString());
                    }
                }
            } else {
                flat.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return flat;
    }

    @Override
    public String successReply() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    @Override
    public String failureReply() {
        return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
    }
}
