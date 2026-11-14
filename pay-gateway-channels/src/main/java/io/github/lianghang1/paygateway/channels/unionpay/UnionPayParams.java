package io.github.lianghang1.paygateway.channels.unionpay;

import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * 云闪付渠道参数（银联开放平台聚合支付）。
 *
 * <p>声明式配置键：{@code sandbox}（true 接沙箱，缺省 false 走生产）、
 * {@code serProvId}（服务商标识，直连商户可空）、{@code merchantId}（商户号），
 * {@code p12CertPath}（商户 P12 证书路径）、{@code p12CertPassword}（证书密码）、
 * {@code publicKey}（平台公钥，Base64 编码的 X.509 公钥串，回调验签用）、
 * {@code notifyUrl}（异步通知地址）。服务商模式在 ChannelConfig 的
 * subMchNo 上指定特约商户号（作为请求的 merId）。</p>
 *
 * @author lianghang
 */
@Data
public class UnionPayParams implements ChannelParams {

    /** 生产网关 */
    public static final String PROD_GATEWAY = "https://partner.95516.com";
    /** 沙箱网关 */
    public static final String SANDBOX_GATEWAY = "http://ysf.bcbip.cn:10240";

    /** 是否沙箱环境 */
    private boolean sandbox;
    /** 服务商标识（直连商户为空） */
    private String serProvId;
    /** 服务商侧商户号（作为渠道侧应用标识参与回调配置反查） */
    private String merchantId;
    /** 商户 P12 证书路径 */
    private String p12CertPath;
    /** P12 证书密码 */
    private String p12CertPassword;
    /** 平台公钥（Base64 的 X.509 公钥串） */
    private String publicKey;
    /** 异步通知地址 */
    private String notifyUrl;

    /** 从配置键值对构建；必填项缺失抛参数异常。 */
    public static UnionPayParams fromMap(Map<String, String> properties) {
        UnionPayParams params = new UnionPayParams();
        if (properties != null) {
            params.setSandbox("true".equalsIgnoreCase(properties.get("sandbox")));
            params.setSerProvId(StringUtils.trimToNull(properties.get("serProvId")));
            params.setMerchantId(properties.get("merchantId"));
            params.setP12CertPath(properties.get("p12CertPath"));
            params.setP12CertPassword(properties.get("p12CertPassword"));
            params.setPublicKey(properties.get("publicKey"));
            params.setNotifyUrl(properties.get("notifyUrl"));
        }
        if (StringUtils.isBlank(params.getMerchantId()) || StringUtils.isBlank(params.getP12CertPath())
                || StringUtils.isBlank(params.getP12CertPassword()) || StringUtils.isBlank(params.getPublicKey())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "云闪付渠道参数不完整，必填键: merchantId/p12CertPath/p12CertPassword/publicKey");
        }
        return params;
    }

    /** 网关地址（按环境切换）。 */
    public String gateway() {
        return sandbox ? SANDBOX_GATEWAY : PROD_GATEWAY;
    }

    @Override
    public String channelAppId() {
        return merchantId;
    }

    /** 关键凭证字段参与相等性判断（签名器缓存复用的依据）。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnionPayParams)) {
            return false;
        }
        UnionPayParams that = (UnionPayParams) other;
        return sandbox == that.sandbox
                && merchantId.equals(that.merchantId)
                && p12CertPath.equals(that.p12CertPath)
                && p12CertPassword.equals(that.p12CertPassword);
    }

    @Override
    public int hashCode() {
        return merchantId.hashCode() * 31 + p12CertPath.hashCode();
    }
}
