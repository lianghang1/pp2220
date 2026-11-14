package io.github.lianghang1.conflux.invoice.model;

/**
 * 渠道授权方式：数电票开票前常见的人机校验，且会话会过期。
 *
 * <p>支付链路没有这一层；这是发票产品必须单独建模的能力。</p>
 *
 * @author lianghang
 */
public enum AuthType {

    /** 无需授权。 */
    NONE("无需授权"),
    /** 短信验证码。 */
    SMS("短信验证码"),
    /** 扫脸/人脸核身。 */
    FACE("扫脸核身"),
    /** 扫码（电子税务局 App / 微信 / 支付宝扫码）。 */
    QR("扫码授权"),
    /** 账号密码/税局登录态。 */
    LOGIN("登录态授权");

    private final String label;

    AuthType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
