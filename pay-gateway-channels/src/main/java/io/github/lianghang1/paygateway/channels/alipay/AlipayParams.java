package io.github.lianghang1.paygateway.channels.alipay;

import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * 支付宝渠道参数（RSA2 公钥模式）。
 *
 * <p>声明式配置键：{@code appId}（开放平台应用 AppID）、{@code privateKey}
 * （应用私钥 PEM 文本）、{@code alipayPublicKey}（支付宝公钥，验签回调用）、
 * {@code signType}（缺省 RSA2）、{@code gateway}（网关地址，缺省正式环境）、
 * {@code notifyUrl}（支付宝异步通知地址，使用方暴露的入口）。</p>
 *
 * @author lianghang
 */
@Data
public class AlipayParams implements ChannelParams {

    public static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";
    public static final String DEFAULT_SIGN_TYPE = "RSA2";

    /** 开放平台应用 AppID */
    private String appId;
    /** 应用私钥（PEM 文本） */
    private String privateKey;
    /** 支付宝公钥（验签回调） */
    private String alipayPublicKey;
    /** 签名类型（RSA2/RSA） */
    private String signType = DEFAULT_SIGN_TYPE;
    /** 网关地址 */
    private String gateway = DEFAULT_GATEWAY;
    /** 支付宝异步通知地址 */
    private String notifyUrl;

    /** 从配置键值对构建；必填项缺失抛参数异常。 */
    public static AlipayParams fromMap(Map<String, String> properties) {
        AlipayParams params = new AlipayParams();
        if (properties != null) {
            params.setAppId(properties.get("appId"));
            params.setPrivateKey(properties.get("privateKey"));
            params.setAlipayPublicKey(properties.get("alipayPublicKey"));
            params.setSignType(StringUtils.isBlank(properties.get("signType"))
                    ? DEFAULT_SIGN_TYPE : properties.get("signType"));
            params.setGateway(StringUtils.isBlank(properties.get("gateway"))
                    ? DEFAULT_GATEWAY : properties.get("gateway"));
            params.setNotifyUrl(properties.get("notifyUrl"));
        }
        if (StringUtils.isBlank(params.getAppId()) || StringUtils.isBlank(params.getPrivateKey())
                || StringUtils.isBlank(params.getAlipayPublicKey())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "支付宝渠道参数不完整，必填键: appId/privateKey/alipayPublicKey");
        }
        return params;
    }

    @Override
    public String channelAppId() {
        return appId;
    }

    /** 关键凭证字段参与相等性判断（客户端缓存复用的依据）。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AlipayParams)) {
            return false;
        }
        AlipayParams that = (AlipayParams) other;
        return appId.equals(that.appId)
                && privateKey.equals(that.privateKey)
                && gateway.equals(that.gateway)
                && signType.equals(that.signType);
    }

    @Override
    public int hashCode() {
        return appId.hashCode() * 31 + signType.hashCode();
    }
}
