package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsParams;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 百旺金穗云 API 网关签名（语雀《1.接口概述》第 4 节）。
 *
 * <p>签名串有严格顺序、需换行、冒号后有空格，date 为格林威治时间且日期位两位：</p>
 * <pre>
 * POST /swgx-saas/.../cloud/queryUniqueSign HTTP/1.1
 * date: Thu, 08 Jan 2024 07:48:16 GMT
 * host: api.baiwangjs.com
 * x-htjs-nonce: 5d4ed749-0afd-4d80-b627-af67e7e2b576
 * x-htjs-ua: Java 1.8.0_181 64 bit, sdk-ver=1.0.0
 * </pre>
 *
 * <p>签名值 = {@code Base64(HmacSHA256(sign_str, SECRET))}（不是 hex），
 * 通过 {@code Authorization: hmac username="KEY", algorithm="hmac-sha256",
 * headers="request-line date host x-htjs-nonce x-htjs-ua", signature="..."} 传递。</p>
 *
 * <p>本类无网络、无 Spring 依赖，所有要素可显式传入以便确定性单测。</p>
 *
 * @author lianghang
 */
public final class BaiwangJsSigner {

    /** RFC 1123 GMT，日期位固定两位（文档明确要求「日期位是两位数」）。 */
    private static final DateTimeFormatter GMT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    public static final String HEADERS_LIST = "request-line date host x-htjs-nonce x-htjs-ua";

    private BaiwangJsSigner() {
    }

    /** 生成 GMT 日期头（UTC）。 */
    public static String gmtDate(ZonedDateTime instant) {
        return GMT.format(instant.withZoneSameInstant(ZoneOffset.UTC));
    }

    /** 组装签名串；顺序与换行不可调整。 */
    public static String signString(String method, String requestLine, String gmtDate,
                                    String host, String nonce, String userAgent) {
        return method + " " + requestLine + " HTTP/1.1\n"
                + "date: " + gmtDate + "\n"
                + "host: " + host + "\n"
                + "x-htjs-nonce: " + nonce + "\n"
                + "x-htjs-ua: " + userAgent;
    }

    /** HmacSHA256 后 Base64 编码（文档要求，区别于常见 hex 输出）。 */
    public static String hmacSha256Base64(String data, String secret) {
        if (data == null || secret == null) {
            throw new IllegalArgumentException("data/secret 不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("百旺网关 HMAC-SHA256 计算失败", e);
        }
    }

    /** 组装 Authorization 头。 */
    public static String authorization(String gatewayKey, String signature) {
        return "hmac username=\"" + gatewayKey + "\", algorithm=\"hmac-sha256\", "
                + "headers=\"" + HEADERS_LIST + "\", signature=\"" + signature + "\"";
    }

    /** 一次性生成全部请求头（含 Content-Type）。 */
    public static Map<String, String> buildHeaders(BaiwangJsParams params, String requestLine,
                                                   ZonedDateTime instant, String nonce) {
        if (StringUtils.isBlank(params.getGatewayKey())
                || StringUtils.isBlank(params.getGatewaySecret())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百旺金穗云缺少网关 KEY/SECRET，请在 attrs.gatewayKey/gatewaySecret 中配置");
        }
        String date = gmtDate(instant);
        String host = StringUtils.isBlank(params.getHost())
                ? "api.baiwangjs.com" : params.getHost();
        String ua = StringUtils.isBlank(params.getUserAgent())
                ? "Java 1.8.0 64 bit, sdk-ver=1.0.0" : params.getUserAgent();
        String actualNonce = StringUtils.isBlank(nonce)
                ? UUID.randomUUID().toString() : nonce;
        String signStr = signString("POST", requestLine, date, host, actualNonce, ua);
        String signature = hmacSha256Base64(signStr, params.getGatewaySecret());

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Authorization", authorization(params.getGatewayKey(), signature));
        headers.put("Date", date);
        headers.put("Host", host);
        headers.put("x-htjs-nonce", actualNonce);
        headers.put("x-htjs-ua", ua);
        return headers;
    }
}
