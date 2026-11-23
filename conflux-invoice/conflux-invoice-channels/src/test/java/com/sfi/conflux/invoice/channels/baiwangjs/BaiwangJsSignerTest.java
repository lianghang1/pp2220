package com.sfi.conflux.invoice.channels.baiwangjs;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.invoice.channels.baiwangjs.support.BaiwangJsSigner;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关签名单测：签名串顺序、GMT 两位日期、HmacSHA256+Base64、Authorization 组装。
 *
 * @author lianghang
 */
class BaiwangJsSignerTest {

    private static final String REQUEST_LINE = BaiwangJsSpec.GATEWAY_PATH_PREFIX
            + "/cloud/queryUniqueSign";
    /**
     * 文档示例的 Date 字面值。其星期（Thu）与真实日历不符（2024-01-08 实际为周一），
     * 这里仅作为固定输入复现文档签名串顺序与 HMAC 向量，不用于校验动态日期计算。
     * 真实请求由 gmtDate() 按 RFC 1123 生成，星期必须与日历一致，否则服务端 Date 校验可能拒绝。
     */
    private static final String DATE = "Thu, 08 Jan 2024 07:48:16 GMT";
    /** 按 RFC 1123 从 UTC 2024-01-08T07:48:16 计算的正确值：日期位两位、星期 Mon。 */
    private static final String COMPUTED_DATE = "Mon, 08 Jan 2024 07:48:16 GMT";
    private static final String HOST = "api.baiwangjs.com";
    private static final String NONCE = "5d4ed749-0afd-4d80-b627-af67e7e2b576";
    private static final String UA = "Java 1.8.0_181 64 bit, sdk-ver=1.0.0";

    @Test
    void signString_matchesDocumentExampleOrder() {
        String signStr = BaiwangJsSigner.signString("POST", REQUEST_LINE, DATE, HOST, NONCE, UA);
        String expected = "POST " + REQUEST_LINE + " HTTP/1.1\n"
                + "date: " + DATE + "\n"
                + "host: " + HOST + "\n"
                + "x-htjs-nonce: " + NONCE + "\n"
                + "x-htjs-ua: " + UA;
        assertEquals(expected, signStr);
    }

    @Test
    void gmtDate_usesTwoDigitDayAndUtc() {
        ZonedDateTime instant = ZonedDateTime.of(2024, 1, 8, 15, 48, 16, 0,
                ZoneOffset.ofHours(8));
        assertEquals(COMPUTED_DATE, BaiwangJsSigner.gmtDate(instant));
    }

    @Test
    void hmacSha256Base64_matchesKnownVector() {
        String signStr = BaiwangJsSigner.signString("POST", REQUEST_LINE, DATE, HOST, NONCE, UA);
        String sign = BaiwangJsSigner.hmacSha256Base64(signStr, "test-secret");
        assertEquals("SkqJMMmRl7oXMGN/lkXUWO8VRzIoBLMCUSZpHUBXd+c=", sign);
        // 输出是 Base64 而非 hex，且可解出 32 字节摘要
        assertEquals(32, Base64.getDecoder().decode(sign).length);
    }

    @Test
    void authorization_matchesDocumentFormat() {
        String auth = BaiwangJsSigner.authorization("my-key", "SIG");
        assertEquals("hmac username=\"my-key\", algorithm=\"hmac-sha256\", "
                + "headers=\"request-line date host x-htjs-nonce x-htjs-ua\", "
                + "signature=\"SIG\"", auth);
    }

    @Test
    void buildHeaders_containsAllDocumentHeaders() {
        BaiwangJsParams params = new BaiwangJsParams()
                .setGatewayKey("my-key").setGatewaySecret("my-secret")
                .setHost(HOST).setUserAgent(UA);
        String requestLine = params.requestLine(BaiwangJsSpec.DEFAULT_BASE_URL
                + "/cloud/queryUniqueSign");
        Map<String, String> headers = BaiwangJsSigner.buildHeaders(params, requestLine,
                ZonedDateTime.of(2024, 1, 8, 7, 48, 16, 0, ZoneOffset.UTC), NONCE);

        assertEquals("Content-Type=application/json; charset=utf-8",
                "Content-Type=" + headers.get("Content-Type"));
        assertEquals(COMPUTED_DATE, headers.get("Date"));
        assertEquals(HOST, headers.get("Host"));
        assertEquals(NONCE, headers.get("x-htjs-nonce"));
        assertEquals(UA, headers.get("x-htjs-ua"));
        assertTrue(headers.get("Authorization").startsWith("hmac username=\"my-key\""));
        assertTrue(headers.get("Authorization").contains("signature=\""));
    }

    @Test
    void buildHeaders_missingCredentials_failsFast() {
        BaiwangJsParams params = new BaiwangJsParams();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> BaiwangJsSigner.buildHeaders(params, REQUEST_LINE,
                        ZonedDateTime.now(), NONCE));
        assertTrue(ex.getMessage().contains("网关 KEY/SECRET"));
    }

    @Test
    void requestLine_usesFullGatewayPath() {
        BaiwangJsParams params = new BaiwangJsParams();
        assertEquals(BaiwangJsSpec.GATEWAY_PATH_PREFIX + "/cloud/v2/applyInvoice",
                params.requestLine(BaiwangJsSpec.DEFAULT_BASE_URL + "/cloud/v2/applyInvoice"));
        assertEquals(BaiwangJsSpec.GATEWAY_PATH_PREFIX + "/cloud/v2/applyInvoice",
                params.requestLine(BaiwangJsSpec.DEFAULT_BASE_URL
                        + "/cloud/v2/applyInvoice?foo=bar"));
    }
}
