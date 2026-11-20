package io.github.lianghang1.conflux.invoice.channels.piaotong.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongParams;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票通信封构建与解析测试（构建 → 解析往返，含验签失败分支）。
 */
class PiaoTongEnvelopeTest {

    private static PiaoTongParams params;

    @BeforeAll
    static void initKeys() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        params = new PiaoTongParams()
                .setPlatformCode("PT000001")
                .setAliasName("ABCD")
                .setPassword("0123456789ABCDEF01234567")
                .setPrivateKey(Base64.getEncoder().encodeToString(
                        pair.getPrivate().getEncoded()))
                .setPublicKey(Base64.getEncoder().encodeToString(
                        pair.getPublic().getEncoded()));
        params.setSellerTaxNo("911100000000000000");
    }

    @Test
    void build_containsEnvelopeFields() {
        Map<String, Object> envelope = PiaoTongEnvelope.build(params,
                "{\"taxpayerNum\":\"911100000000000000\"}");
        assertEquals("PT000001", envelope.get("platformCode"));
        assertEquals("RSA", envelope.get("signType"));
        assertEquals("JSON", envelope.get("format"));
        assertEquals("1.0", envelope.get("version"));
        assertNotNull(envelope.get("content"));
        assertNotNull(envelope.get("timestamp"));
        assertNotNull(envelope.get("serialNo"));
        assertNotNull(envelope.get("sign"));
        String serialNo = String.valueOf(envelope.get("serialNo"));
        assertTrue(serialNo.startsWith("ABCD"));
        assertEquals(26, serialNo.length());
    }

    @Test
    void invoiceSerialNo_20chars() {
        String serial = PiaoTongEnvelope.invoiceSerialNo(params);
        assertTrue(serial.startsWith("ABCD"));
        assertEquals(20, serial.length());
    }

    @Test
    void parse_roundtrip() {
        String business = "{\"taxpayerNum\":\"911100000000000000\","
                + "\"code\":\"0000\",\"msg\":\"开具成功\",\"invoiceNo\":\"12345678\"}";
        Map<String, Object> envelope = PiaoTongEnvelope.build(params, business);
        // 真实响应信封带 code/msg 且由票通侧签名：先补字段再按响应方私钥重签
        envelope.put("code", PiaoTongSpec.ST_SUCCESS);
        envelope.put("msg", "受理成功");
        java.util.Map<String, String> signSource = new java.util.TreeMap<String, String>();
        for (Map.Entry<String, Object> entry : envelope.entrySet()) {
            signSource.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        envelope.put("sign", PiaoTongCipher.sign(
                PiaoTongCipher.signatureContent(signSource), params.getPrivateKey()));
        PiaoTongResponse response = PiaoTongEnvelope.parse(
                JsonUtils.toJson(envelope), params, true);
        assertEquals(PiaoTongSpec.ST_SUCCESS, response.getCode());
        assertEquals("受理成功", response.getMessage());
        assertNotNull(response.getRoot());
        assertEquals("0000", response.bizText("code"));
        assertEquals("开具成功", response.bizText("msg"));
        assertEquals("12345678", response.bizText("invoiceNo"));
    }

    @Test
    void parse_tamperedSign_throwsSignInvalid() {
        String business = "{\"code\":\"0000\"}";
        Map<String, Object> envelope = PiaoTongEnvelope.build(params, business);
        envelope.put("sign", "TAMPERED");
        GatewayException e = assertThrows(GatewayException.class,
                () -> PiaoTongEnvelope.parse(JsonUtils.toJson(envelope), params, true));
        assertEquals(InvoiceErrorCode.SIGN_INVALID, e.getErrorCode());
    }

    @Test
    void parse_nonEnvelope_throwsChannelCallFailed() {
        assertThrows(GatewayException.class,
                () -> PiaoTongEnvelope.parse("not-json", params, true));
        assertThrows(GatewayException.class,
                () -> PiaoTongEnvelope.parse("[1,2,3]", params, true));
    }

    @Test
    void parse_emptyContent_rootNull() {
        Map<String, Object> envelope = new java.util.LinkedHashMap<String, Object>();
        envelope.put("code", PiaoTongSpec.ST_SUCCESS);
        envelope.put("msg", "受理成功");
        // 无 content/sign 的极简信封（兼容降级应答）
        PiaoTongResponse response = PiaoTongEnvelope.parse(
                JsonUtils.toJson(envelope), params, true);
        assertEquals(PiaoTongSpec.ST_SUCCESS, response.getCode());
        assertEquals(null, response.getRoot());
    }

    @Test
    void text_nullSafe() {
        JsonNode node = JsonUtils.readTree("{\"a\":\"1\"}");
        assertEquals("1", PiaoTongEnvelope.text(node, "a"));
        assertEquals(null, PiaoTongEnvelope.text(node, "b"));
        assertEquals(null, PiaoTongEnvelope.text(null, "a"));
    }
}
