package io.github.lianghang1.conflux.pay.channels.chinaums;

import io.github.lianghang1.conflux.common.util.SignUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * 天满开放平台 OPEN-BODY-SIG 请求签名器。
 *
 * <p>算法（天满开发者中心 → 接入指引 → 平台认证方式）：
 * <pre>
 *   B = SHA256_WITH_LOWER_HEXSTR(报文体字节)
 *   C = appId + timestamp + nonce + B
 *   E = HmacSHA256(C, appKey)
 *   Signature = Base64(E)
 *   Authorization = OPEN-BODY-SIG AppId="..." Timestamp="..." Nonce="..." Signature="..."
 * </pre>
 * timestamp 格式 yyyyMMddHHmmss，nonce 为 32 位随机串（去横线 UUID）。
 * 本类无状态、线程安全。</p>
 *
 * @author lianghang
 */
public final class ChinaumsSigner {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ChinaumsSigner() {
    }

    /** 签名上下文：一次请求内 timestamp/nonce 与 Authorization 一致。 */
    public static final class Signed {
        public final String authorization;
        public final String timestamp;
        public final String nonce;

        Signed(String authorization, String timestamp, String nonce) {
            this.authorization = authorization;
            this.timestamp = timestamp;
            this.nonce = nonce;
        }
    }

    /** 按报文体 JSON 串生成 OPEN-BODY-SIG 认证头。 */
    public static Signed sign(String body, ChinaumsParams params) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String b = SignUtil.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String c = params.getAppId() + timestamp + nonce + b;
        String signature = base64HmacSha256(c, params.getAppKey());
        String authorization = "OPEN-BODY-SIG AppId=\"" + params.getAppId()
                + "\", Timestamp=\"" + timestamp
                + "\", Nonce=\"" + nonce
                + "\", Signature=\"" + signature + "\"";
        return new Signed(authorization, timestamp, nonce);
    }

    /** 文档对向量用：B=SHA256_HEX([65]) 应为 559aead082...fdffd。 */
    static String sha256HexOf(byte[] bytes) {
        return SignUtil.sha256Hex(bytes);
    }

    /** HmacSHA256 后 Base64 编码（签名值）。 */
    static String base64HmacSha256(String data, String secret) {
        // SignUtil.hmacSha256Hex 给的是 hex，这里需要原始字节做 Base64，故自行 MAC。
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("天满 HMAC-SHA256 签名失败", e);
        }
    }
}
