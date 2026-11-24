package com.sfi.conflux.platform.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.sfi.conflux.platform.core.PlatformCredential;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AlipayClients 门面单测：公钥模式真实构造并写入凭证、复用、凭证变更重建、attrs 纳入指纹。
 * 证书模式需真实证书文件，由 auth 域 mock 渠道测试兜底调用路径，此处不构造。
 *
 * @author lianghang
 */
class AlipayClientsTest {

    private final AlipayClients clients = new AlipayClients();

    @Test
    void publicKeyModeWritesCredentialIntoNativeClient() throws Exception {
        PlatformCredential c = PlatformCredential.of("app123", "app-private-key");
        AlipayClient client = clients.client(c);

        assertTrue(client instanceof DefaultAlipayClient);
        assertEquals("app-private-key", readField(client, "privateKey"));
    }

    @Test
    void alipayPublicKeyFromAttrsIsWritten() throws Exception {
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("alipayPublicKey", "ali-public-key");
        AlipayClient client = clients.client(PlatformCredential.of("app", "priv", attrs));

        assertEquals("ali-public-key", readField(client, "alipayPublicKey"));
    }

    @Test
    void sameCredentialReusesInstance() {
        PlatformCredential c = PlatformCredential.of("app", "priv");
        AlipayClient a = clients.client(c);
        AlipayClient b = clients.client(c);
        assertSame(a, b);
    }

    @Test
    void changedCredentialRebuilds() {
        AlipayClient a = clients.client(PlatformCredential.of("app", "priv1"));
        AlipayClient b = clients.client(PlatformCredential.of("app", "priv2"));
        assertNotSame(a, b);
    }

    @Test
    void changedAttrsRebuilds() {
        Map<String, String> attrs1 = new LinkedHashMap<String, String>();
        attrs1.put("alipayPublicKey", "pub1");
        Map<String, String> attrs2 = new LinkedHashMap<String, String>();
        attrs2.put("alipayPublicKey", "pub2");

        AlipayClient a = clients.client(PlatformCredential.of("app", "priv", attrs1));
        AlipayClient b = clients.client(PlatformCredential.of("app", "priv", attrs2));

        assertNotSame(a, b);
        assertEquals("pub1", readFieldUnchecked(a, "alipayPublicKey"));
        assertEquals("pub2", readFieldUnchecked(b, "alipayPublicKey"));
    }

    @Test
    void customGatewayAndSignTypeAccepted() {
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("gateway", "https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        attrs.put("signType", "RSA2");
        attrs.put("alipayPublicKey", "pub");
        AlipayClient client = clients.client(PlatformCredential.of("app", "priv", attrs));
        // 构造成功即证明自定义网关/签名类型被接受（SDK 构造函数不校验网关可达性）
        assertTrue(client instanceof DefaultAlipayClient);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field f = DefaultAlipayClient.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object readFieldUnchecked(Object target, String name) {
        try {
            return readField(target, name);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
