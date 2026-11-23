package com.sfi.conflux.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 签名工具：HMAC-SHA256 / SHA-256 摘要（十六进制小写输出）。
 *
 * <p>供下游通知签名与渠道包内通用摘要场景使用；渠道专属复杂签名
 * （RSA2、证书体系）由各渠道包基于其 SDK 自行实现。</p>
 *
 * @author lianghang
 */
public final class SignUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SignUtil() {
    }

    /** HMAC-SHA256 摘要（hex 小写）；任一入参为 null 抛参数异常。 */
    public static String hmacSha256Hex(String data, String secret) {
        if (data == null || secret == null) {
            throw new IllegalArgumentException("data/secret 不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /** SHA-256 摘要（hex 小写）。 */
    public static String sha256Hex(String data) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为空");
        }
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 摘要（字节输入，用于二进制文件完整性核算）。 */
    public static String sha256Hex(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    /**
     * 常量时间 hex 比较（不区分大小写）：验签结果比对专用，
     * 消除逐字符 equals 的时序侧信道。任一入参为 null 返回 false。
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
