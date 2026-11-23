package com.sfi.conflux.pay.channels.chinaums;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.http.HttpResponse;
import com.sfi.conflux.common.http.HttpTransport;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 天满开放平台 HTTP 客户端：公共参数 → OPEN-BODY-SIG 签名 → POST JSON → errCode 业务判定。
 *
 * <p>线程安全：仅用入参与局部变量，params 从 config 读取；无共享可变状态。</p>
 *
 * @author lianghang
 */
@Slf4j
public class ChinaumsClient {

    /** 天满公共端点（相对网关前缀） */
    public static final String API_QUERY = "/v1/netpay/query";
    public static final String API_REFUND = "/v1/netpay/refund";
    public static final String API_REFUND_QUERY = "/v1/netpay/refund-query";
    public static final String API_CLOSE = "/v1/netpay/close";
    public static final String API_SUB_ORDERS_CONFIRM = "/v1/netpay/sub-orders-confirm";

    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CONNECT_MILLIS = 10000L;
    private static final long READ_MILLIS = 20000L;
    private static final long WRITE_MILLIS = 10000L;

    /** 执行调用，返回应答根节点（已确认 errCode=SUCCESS）。 */
    public JsonNode execute(String action, String apiUri, ChannelConfig config, Map<String, Object> business) {
        ChinaumsParams params = paramsOf(config);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        // 公共参数（放最前，顺序不影响签名，仅便于阅读）
        body.put("requestTimestamp", LocalDateTime.now().format(REQUEST_TIME));
        body.put("mid", params.getMid());
        body.put("tid", params.getTid());
        body.put("msgId", UUID.randomUUID().toString().replace("-", ""));
        String json = JsonUtils.toJson(bodyWithBusiness(body, business));
        String url = params.effectiveGateway(config.isSandbox()) + apiUri;
        ChinaumsSigner.Signed signed = ChinaumsSigner.sign(json, params);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", signed.authorization);
        headers.put("Content-Type", HttpTransport.JSON);
        try {
            HttpResponse response = HttpTransport.postBytes(url, headers, HttpTransport.JSON,
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    CONNECT_MILLIS, READ_MILLIS, WRITE_MILLIS);
            String text = response.getBody();
            log.info("天满{}完成: url={}, resp={}", action, apiUri, text);
            if (!response.isSuccessful() || StringUtils.isBlank(text)) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "天满{}HTTP异常: status={}, body={}", action, response.getStatusCode(), text);
            }
            JsonNode root = JsonUtils.readTree(text);
            if (root == null || !root.isObject()) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "天满{}应答非 JSON 对象: {}", action, text);
            }
            String errCode = text(root, "errCode");
            if (!ChinaumsStateTranslator.isSuccess(errCode)) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "天满{}失败: errCode={}, errMsg={}", action, errCode, text(root, "errMsg"));
            }
            return root;
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "天满{}调用失败: {}", action, e.getMessage(), e);
        }
    }

    /**
     * 构造 GET 跳转链接（OPEN-FORM-PARAM 认证）：把业务报文做 content 签名，
     * 拼进 URL query，返回供浏览器跳转的完整链接。GET 下单端点（三种 H5）均适用。
     *
     * <p>公式（平台认证方式 → GET 请求类型的认证方式）：
     * signature = URLEncoder(Base64_Encode(HmacSHA256(appId+timestamp+nonce+SHA256_HEX(content), appKey)))。</p>
     */
    public String buildGetUrl(String apiUri, ChannelConfig config, Map<String, Object> business) {
        ChinaumsParams params = paramsOf(config);
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("requestTimestamp", LocalDateTime.now().format(REQUEST_TIME));
        content.put("mid", params.getMid());
        content.put("tid", params.getTid());
        if (business != null) {
            for (Map.Entry<String, Object> e : business.entrySet()) {
                if (e.getValue() != null) {
                    content.put(e.getKey(), e.getValue());
                }
            }
        }
        String contentJson = JsonUtils.toJson(content);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String b = com.sfi.conflux.common.util.SignUtil.sha256Hex(
                contentJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String rawSignature = ChinaumsSigner.base64HmacSha256(
                params.getAppId() + timestamp + nonce + b, params.getAppKey());
        return params.effectiveGateway(config.isSandbox()) + apiUri
                + "?authorization=OPEN-FORM-PARAM&appId=" + params.getAppId()
                + "&timestamp=" + timestamp + "&nonce=" + nonce
                + "&content=" + urlEncode(contentJson)
                + "&signature=" + urlEncode(rawSignature);
    }

    private static Map<String, Object> bodyWithBusiness(Map<String, Object> base, Map<String, Object> business) {
        Map<String, Object> all = new LinkedHashMap<String, Object>(base);
        if (business != null) {
            for (Map.Entry<String, Object> e : business.entrySet()) {
                if (e.getValue() != null) {
                    all.put(e.getKey(), e.getValue());
                }
            }
        }
        return all;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("URL 编码失败", e);
        }
    }

    /** 从 JSON 节点取文本字段。 */
    public static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** 从 JSON 节点取长整数字段（兼容字符串与数字）。 */
    public static Long longValue(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return v.isNumber() ? v.asLong() : Long.valueOf(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static ChinaumsParams paramsOf(ChannelConfig config) {
        if (config instanceof ChinaumsParams) {
            return (ChinaumsParams) config;
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "天满渠道参数类型不匹配: {}",
                config == null ? "null" : config.getClass().getName());
    }
}
