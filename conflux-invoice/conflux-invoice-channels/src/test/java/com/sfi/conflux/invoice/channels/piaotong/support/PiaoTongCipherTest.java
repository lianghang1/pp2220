package com.sfi.conflux.invoice.channels.piaotong.support;

import com.sfi.conflux.common.exception.GatewayException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票通加解密与签名测试（对照文档 1.1 签名串示例与生产实现）。
 */
class PiaoTongCipherTest {

    private static final String DES_KEY = "0123456789ABCDEF01234567";

    @Test
    void signatureContent_sortedByAscii() {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("version", "1.0");
        params.put("content", "ENC");
        params.put("timestamp", "2024-01-08 07:48:16");
        params.put("signType", "RSA");
        params.put("platformCode", "PT000001");
        params.put("serialNo", "ABCD20240108074816abcd1234");
        params.put("format", "JSON");
        assertEquals("content=ENC&format=JSON&platformCode=PT000001"
                        + "&serialNo=ABCD20240108074816abcd1234"
                        + "&signType=RSA&timestamp=2024-01-08 07:48:16&version=1.0",
                PiaoTongCipher.signatureContent(params));
    }

    @Test
    void signatureContent_excludesSign() {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("sign", "SHOULD_NOT_APPEAR");
        params.put("format", "JSON");
        assertEquals("format=JSON", PiaoTongCipher.signatureContent(params));
    }

    @Test
    void des_roundtrip() {
        String plain = "{\"taxpayerNum\":\"911100000000000000\",\"buyerName\":\"北京测试公司\"}";
        String encrypted = PiaoTongCipher.encrypt3Des(DES_KEY, plain);
        assertTrue(Base64.getDecoder().decode(encrypted).length > 0);
        assertEquals(plain, PiaoTongCipher.decrypt3Des(DES_KEY, encrypted));
    }

    @Test
    void des_wrongKey_fails() {
        String encrypted = PiaoTongCipher.encrypt3Des(DES_KEY, "hello");
        assertThrows(GatewayException.class,
                () -> PiaoTongCipher.decrypt3Des("999999999999999999999999", encrypted));
    }

    @Test
    void rsa_signAndVerify_roundtrip() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(
                pair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(
                pair.getPublic().getEncoded());
        String content = "content=ABC&format=JSON&platformCode=PT000001&version=1.0";
        String sign = PiaoTongCipher.sign(content, privateKey);
        assertTrue(PiaoTongCipher.verify(content, sign, publicKey));
        assertFalse(PiaoTongCipher.verify(content + "&format=JSON", sign, publicKey));
    }

    @Test
    void rsa_verify_badKey_returnsFalse() {
        assertFalse(PiaoTongCipher.verify("content", "not-a-sign",
                "not-a-key"));
    }
}
