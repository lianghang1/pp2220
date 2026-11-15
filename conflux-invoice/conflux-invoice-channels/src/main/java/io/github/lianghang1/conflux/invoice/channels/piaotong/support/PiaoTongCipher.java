package io.github.lianghang1.conflux.invoice.channels.piaotong.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 票通协议加解密与签名（文档 1.1 加解密说明 + 生产实现逐字段核对）。
 *
 * <ul>
 *   <li>业务报文 3DES（{@code DESede/ECB/PKCS5Padding}，UTF-8），密钥为配置的
 *       {@code password}，结果 Base64；</li>
 *   <li>签名 {@code SHA1withRSA}，签名串 = 除 {@code sign} 外全部参数按 key
 *       字典序拼 {@code k=v&k=v}，结果 Base64；</li>
 *   <li>响应/回调验签用票通平台公钥（X509），请求签名用我方私钥（PKCS8）。</li>
 * </ul>
 *
 * @author lianghang
 */
public final class PiaoTongCipher {

    private static final String ALGORITHM_3DES = "DESede";
    private static final String SIGN_ALGORITHMS = "SHA1withRSA";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private PiaoTongCipher() {
    }

    // ==================== 3DES ====================

    /** 3DES-ECB 加密并 Base64。 */
    public static String encrypt3Des(String key, String plain) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM_3DES);
            cipher.init(Cipher.ENCRYPT_MODE, desKey(key));
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plain.getBytes(CHARSET)));
        } catch (Exception e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "票通 3DES 加密失败: " + e.getMessage(), e);
        }
    }

    /** 3DES-ECB 解密（输入 Base64）。 */
    public static String decrypt3Des(String key, String cipherTextBase64) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM_3DES);
            cipher.init(Cipher.DECRYPT_MODE, desKey(key));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return new String(plain, CHARSET);
        } catch (Exception e) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "票通 3DES 解密失败（检查 password 配置）: " + e.getMessage(), e);
        }
    }

    private static SecretKey desKey(String key) {
        return new SecretKeySpec(key.getBytes(CHARSET), ALGORITHM_3DES);
    }

    // ==================== RSA 签名 / 验签 ====================

    /** SHA1withRSA 私钥签名（PKCS8 Base64 私钥），返回 Base64 签名。 */
    public static String sign(String content, String privateKeyBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey key = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
            Signature signature = Signature.getInstance(SIGN_ALGORITHMS);
            signature.initSign(key);
            signature.update(content.getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "票通 RSA 签名失败（检查 privateKey 配置）: " + e.getMessage(), e);
        }
    }

    /** SHA1withRSA 平台公钥验签（X509 Base64 公钥）。 */
    public static boolean verify(String content, String signBase64, String publicKeyBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey key = factory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            Signature signature = Signature.getInstance(SIGN_ALGORITHMS);
            signature.initVerify(key);
            signature.update(content.getBytes(CHARSET));
            return signature.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 签名串：除 {@code sign} 外全部参数按 key 字典序拼 {@code k=v&k=v}。
     * 文档示例：{@code content=...&format=JSON&platformCode=...&serialNo=...
     * &signType=RSA&timestamp=...&version=1.0}
     */
    public static String signatureContent(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<String, String>(params);
        sorted.remove("sign");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /** 排序键名（供测试断言）。 */
    public static List<String> sortedKeys(Map<String, String> params) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareTo(b);
            }
        });
        return keys;
    }
}
