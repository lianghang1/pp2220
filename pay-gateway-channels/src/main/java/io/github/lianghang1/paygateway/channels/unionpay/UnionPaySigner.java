package io.github.lianghang1.paygateway.channels.unionpay;

import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云闪付签名工具：请求签名（商户 P12 证书私钥）与回调验签（平台公钥）。
 *
 * <p>签名流程：参数按 key 字典序排列（含空值，排除 signature）拼为
 * {@code k1=v1&k2=v2} → SHA-256 摘要转 16 进制 → 私钥 SHA256withRSA 签名
 * → Base64 编码。验签流程与签名串构建一致，用平台公钥（Base64 的
 * X.509 公钥串）校验回调报文中的 signature。全部方法线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class UnionPaySigner {

    private static final String SIGNATURE_FIELD = "signature";
    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final ConcurrentHashMap<UnionPayParams, PrivateKey> privateKeys =
            new ConcurrentHashMap<UnionPayParams, PrivateKey>();

    /** 对请求参数签名并写入 signature 字段（返回新 Map，不改入参）。 */
    public Map<String, Object> sign(Map<String, Object> requestParams, UnionPayParams params) {
        Map<String, Object> signed = new TreeMap<String, Object>(requestParams);
        signed.put(SIGNATURE_FIELD, signValue(signed, params));
        return signed;
    }

    /** 计算签名值。 */
    public String signValue(Map<String, Object> params, UnionPayParams paramsConf) {
        try {
            byte[] digest = sha256Hex(buildSignatureString(params));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(paramsConf));
            signature.update(digest);
            return BASE64.encodeToString(signature.sign());
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "云闪付签名失败: {}", e.getMessage(), e);
        }
    }

    /** 平台公钥验签（params 含 signature 字段）；验签通过返回 true。 */
    public boolean verify(Map<String, Object> params, UnionPayParams paramsConf) {
        Object signatureObj = params.get(SIGNATURE_FIELD);
        if (!(signatureObj instanceof String) || ((String) signatureObj).trim().isEmpty()) {
            return false;
        }
        try {
            byte[] digest = sha256Hex(buildSignatureString(params));
            byte[] encodedKey = BASE64_DECODER.decode(paramsConf.getPublicKey());
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encodedKey));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(digest);
            return signature.verify(BASE64_DECODER.decode((String) signatureObj));
        } catch (Exception e) {
            log.warn("云闪付回调验签异常: {}", e.getMessage());
            return false;
        }
    }

    /** 签名串：key 字典序、排除 signature、含空值、k=v 以 & 连接。 */
    private String buildSignatureString(Map<String, Object> params) {
        TreeMap<String, Object> sorted = new TreeMap<String, Object>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (SIGNATURE_FIELD.equals(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private byte[] sha256Hex(String data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 从 P12 证书加载签名私钥（按证书参数缓存复用）。 */
    private PrivateKey loadPrivateKey(UnionPayParams params) {
        return privateKeys.computeIfAbsent(params, key -> {
            try (FileInputStream stream = new FileInputStream(key.getP12CertPath())) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(stream, key.getP12CertPassword().toCharArray());
                java.util.Enumeration<String> aliases = keyStore.aliases();
                if (!aliases.hasMoreElements()) {
                    throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                            "云闪付 P12 证书无可用别名: {}", key.getP12CertPath());
                }
                return (PrivateKey) keyStore.getKey(aliases.nextElement(),
                        key.getP12CertPassword().toCharArray());
            } catch (GatewayException e) {
                throw e;
            } catch (Exception e) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "云闪付 P12 证书加载失败: {}", e.getMessage());
            }
        });
    }
}
