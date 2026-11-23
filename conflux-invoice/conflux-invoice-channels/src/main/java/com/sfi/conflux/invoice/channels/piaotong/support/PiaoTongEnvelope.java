package com.sfi.conflux.invoice.channels.piaotong.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.invoice.channels.piaotong.PiaoTongParams;
import com.sfi.conflux.invoice.channels.piaotong.PiaoTongSpec;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 票通信封构建与解析（文档 1.1/1.2 全局公共参数）。
 *
 * <p>请求信封（顺序固定，便于排障）：{@code platformCode / signType / format /
 * version / content / timestamp / serialNo / sign}；签名串为除 sign 外全部参数
 * 按 key 字典序 {@code k=v&k=v}。响应信封验签后解出业务报文。</p>
 *
 * @author lianghang
 */
public final class PiaoTongEnvelope {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private PiaoTongEnvelope() {
    }

    // ==================== 请求信封 ====================

    /** 构建带签名的请求信封（{@code content} 为业务报文 JSON）。 */
    public static Map<String, Object> build(PiaoTongParams params, String businessJson) {
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("platformCode", params.getPlatformCode());
        envelope.put("signType", "RSA");
        envelope.put("format", "JSON");
        envelope.put("version", "1.0");
        envelope.put("content", PiaoTongCipher.encrypt3Des(params.getPassword(), businessJson));
        envelope.put("timestamp", LocalDateTime.now().format(TIMESTAMP));
        envelope.put("serialNo", serialNo(params));
        Map<String, String> signSource = new TreeMap<String, String>();
        for (Map.Entry<String, Object> entry : envelope.entrySet()) {
            signSource.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        envelope.put("sign", PiaoTongCipher.sign(
                PiaoTongCipher.signatureContent(signSource), params.getPrivateKey()));
        return envelope;
    }

    /** 26 位交易请求流水号：4 位平台简称 + 14 位日期时间 + 8 位 UUID。 */
    public static String serialNo(PiaoTongParams params) {
        return params.getAliasName() + LocalDateTime.now().format(COMPACT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 20 位发票请求流水号（{@code invoiceReqSerialNo}）：
     * 4 位平台简称 + 14 位日期时间 + 2 位随机数，同一次业务全程复用。
     */
    public static String invoiceSerialNo(PiaoTongParams params) {
        String suffix = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 100));
        return params.getAliasName() + LocalDateTime.now().format(COMPACT) + suffix;
    }

    // ==================== 响应 / 回调信封解析 ====================

    /**
     * 解析响应/回调信封：验签（平台公钥）→ 3DES 解密 content → 业务报文 JSON 树。
     *
     * @param rawBody      HTTP 原始响应体（信封 JSON）
     * @param params       渠道配置（提供 password/publicKey）
     * @param failOnVerify 验签失败是否抛异常（同步响应 true；解析失败可降级场景传 false 由调用方处理）
     */
    public static PiaoTongResponse parse(String rawBody, PiaoTongParams params,
                                         boolean failOnVerify) {
        JsonNode envelope;
        try {
            envelope = JsonUtils.readTree(rawBody);
        } catch (IllegalArgumentException e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "票通返回非 JSON 信封: " + abbreviate(rawBody), e);
        }
        if (envelope == null || !envelope.isObject()) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "票通信封不是 JSON 对象: " + abbreviate(rawBody));
        }
        PiaoTongResponse response = new PiaoTongResponse()
                .setCode(text(envelope, "code"))
                .setMessage(text(envelope, "msg"))
                .setSerialNo(text(envelope, "serialNo"))
                .setRawBody(rawBody);

        String content = text(envelope, "content");
        String sign = text(envelope, "sign");
        if (StringUtils.isNotBlank(sign) && StringUtils.isNotBlank(params.getPublicKey())) {
            Map<String, String> signSource = new TreeMap<String, String>();
            java.util.Iterator<Map.Entry<String, JsonNode>> it = envelope.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                if (!"sign".equals(field.getKey()) && field.getValue().isValueNode()) {
                    signSource.put(field.getKey(), field.getValue().asText());
                }
            }
            boolean ok = PiaoTongCipher.verify(
                    PiaoTongCipher.signatureContent(signSource), sign, params.getPublicKey());
            if (!ok) {
                if (failOnVerify) {
                    throw new GatewayException(InvoiceErrorCode.SIGN_INVALID,
                            "票通响应验签失败: serialNo=" + response.getSerialNo());
                }
                response.setMessage((response.getMessage() == null ? "" : response.getMessage())
                        + " [响应验签失败]");
            }
        }
        if (StringUtils.isNotBlank(content)) {
            String business = PiaoTongCipher.decrypt3Des(params.getPassword(), content);
            try {
                response.setRoot(JsonUtils.readTree(business));
            } catch (IllegalArgumentException e) {
                throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                        "票通业务报文解密后非 JSON: " + abbreviate(business), e);
            }
        }
        return response;
    }

    /** 业务报文字段读取（value 节点才返回）。 */
    public static String text(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isEmpty() ? null : text;
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
