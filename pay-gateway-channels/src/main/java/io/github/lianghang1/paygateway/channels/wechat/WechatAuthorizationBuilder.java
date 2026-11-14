package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 V3 请求授权头构建器：以商户私钥对
 * 「方法\n路径\n时间戳\n随机串\n报文\n」签名（SHA256withRSA → Base64），
 * 组装 {@code WECHATPAY2-SHA256-RSA2048} Authorization 头——用于 WxJava
 * 未封装的接口（如分账结果查询）的直连调用。全部方法线程安全。
 *
 * @author lianghang
 */
public class WechatAuthorizationBuilder {

    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final ConcurrentHashMap<WechatParams, PrivateKey> privateKeys =
            new ConcurrentHashMap<WechatParams, PrivateKey>();

    /** 构建 Authorization 头（body 为空时传空串）。 */
    public String buildAuthorization(WechatParams params, String method,
                                     String urlPathWithQuery, String body) {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String signStr = method + "\n" + urlPathWithQuery + "\n" + timestamp + "\n"
                + nonce + "\n" + (body == null ? "" : body) + "\n";
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(params));
            signature.update(signStr.getBytes(StandardCharsets.UTF_8));
            String sign = BASE64.encodeToString(signature.sign());
            return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + params.getMerchantId()
                    + "\",nonce_str=\"" + nonce
                    + "\",timestamp=\"" + timestamp
                    + "\",serial_no=\"" + params.getMerchantSerialNumber()
                    + "\",signature=\"" + sign + "\"";
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信 Authorization 构建失败: {}", e.getMessage(), e);
        }
    }

    /** 商户私钥 PEM 解析（按参数缓存复用）。 */
    public PrivateKey loadPrivateKey(WechatParams params) {
        return privateKeys.computeIfAbsent(params, key -> {
            String normalized = key.getMerchantPrivateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            if (StringUtils.isBlank(normalized)) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "微信商户私钥未配置或为空");
            }
            try {
                byte[] encoded = BASE64_DECODER.decode(normalized);
                return KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            } catch (Exception e) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "微信商户私钥解析失败: {}", e.getMessage());
            }
        });
    }
}
