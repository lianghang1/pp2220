package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * 微信支付渠道参数（APIv3 证书模式）。
 *
 * <p>声明式配置键：{@code merchantId}（商户号）、{@code merchantSerialNumber}
 * （商户 API 证书序列号）、{@code merchantPrivateKey}（商户 API 私钥 PEM 文本，
 * 换行可用 {@code \n} 转义）、{@code apiV3Key}（APIv3 密钥）、{@code appId}
 * （微信应用 AppID）、{@code notifyUrl}（微信回调地址，使用方暴露的入口）。
 * 服务商模式在 ChannelConfig 的 subMchNo 上指定特约商户号。</p>
 *
 * @author lianghang
 */
@Data
public class WechatParams implements ChannelParams {

    /** 商户号 */
    private String merchantId;
    /** 商户 API 证书序列号 */
    private String merchantSerialNumber;
    /** 商户 API 私钥（PEM 文本） */
    private String merchantPrivateKey;
    /** APIv3 密钥 */
    private String apiV3Key;
    /** 微信应用 AppID */
    private String appId;
    /** 微信回调地址（支付/退款/转账/分账共用入口） */
    private String notifyUrl;

    /** 从配置键值对构建；必填项缺失抛参数异常。 */
    public static WechatParams fromMap(Map<String, String> properties) {
        WechatParams params = new WechatParams();
        if (properties != null) {
            params.setMerchantId(properties.get("merchantId"));
            params.setMerchantSerialNumber(properties.get("merchantSerialNumber"));
            params.setMerchantPrivateKey(properties.get("merchantPrivateKey"));
            params.setApiV3Key(properties.get("apiV3Key"));
            params.setAppId(properties.get("appId"));
            params.setNotifyUrl(properties.get("notifyUrl"));
        }
        if (StringUtils.isBlank(params.getMerchantId()) || StringUtils.isBlank(params.getMerchantSerialNumber())
                || StringUtils.isBlank(params.getMerchantPrivateKey()) || StringUtils.isBlank(params.getApiV3Key())
                || StringUtils.isBlank(params.getAppId())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信渠道参数不完整，必填键: merchantId/merchantSerialNumber/merchantPrivateKey/apiV3Key/appId");
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
        if (!(other instanceof WechatParams)) {
            return false;
        }
        WechatParams that = (WechatParams) other;
        return merchantId.equals(that.merchantId)
                && merchantSerialNumber.equals(that.merchantSerialNumber)
                && merchantPrivateKey.equals(that.merchantPrivateKey)
                && apiV3Key.equals(that.apiV3Key);
    }

    @Override
    public int hashCode() {
        return merchantId.hashCode() * 31 + merchantSerialNumber.hashCode();
    }
}
