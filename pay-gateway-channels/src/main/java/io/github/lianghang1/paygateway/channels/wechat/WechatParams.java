package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * 微信支付渠道参数（V2/V3 双版本，经 WxJava 封装接入）。
 *
 * <p>声明式配置键：{@code merchantId}（商户号）、{@code appId}（微信应用 AppID）、
 * {@code apiV2Key}（V2 商户支付密钥，wxApiVersion=v2 或付款码/企业付款时必填）、
 * {@code apiV3Key}（APIv3 密钥，v3 时必填）、{@code merchantSerialNumber}
 * （V3 商户 API 证书序列号）、{@code merchantPrivateKey}（V3 商户 API 私钥 PEM 文本），
 * {@code entPayKey}（V2 企业付款密钥，缺省复用 apiV2Key）、
 * {@code notifyUrl}（微信回调地址）、{@code wxApiVersion}（v2/v3，缺省 v3）。
 * 服务商模式在 ChannelConfig 的 subMchNo 上指定特约商户号。</p>
 *
 * @author lianghang
 */
@Data
public class WechatParams implements io.github.lianghang1.paygateway.channel.ChannelParams {

    /** V2 协议版本标识 */
    public static final String API_VERSION_V2 = "v2";
    /** V3 协议版本标识（缺省） */
    public static final String API_VERSION_V3 = "v3";

    /** 商户号 */
    private String merchantId;
    /** 微信应用 AppID */
    private String appId;
    /** V2 商户支付密钥 */
    private String apiV2Key;
    /** V2 企业付款密钥（缺省复用 apiV2Key） */
    private String entPayKey;
    /** APIv3 密钥 */
    private String apiV3Key;
    /** V3 商户 API 证书序列号 */
    private String merchantSerialNumber;
    /** V3 商户 API 私钥（PEM 文本） */
    private String merchantPrivateKey;
    /** 微信回调地址 */
    private String notifyUrl;
    /** 协议版本（v2/v3，缺省 v3） */
    private String wxApiVersion = API_VERSION_V3;

    /** 从配置键值对构建；按版本校验必填项。 */
    public static WechatParams fromMap(Map<String, String> properties) {
        WechatParams params = new WechatParams();
        if (properties != null) {
            params.setMerchantId(properties.get("merchantId"));
            params.setAppId(properties.get("appId"));
            params.setApiV2Key(properties.get("apiV2Key"));
            params.setEntPayKey(properties.get("entPayKey"));
            params.setApiV3Key(properties.get("apiV3Key"));
            params.setMerchantSerialNumber(properties.get("merchantSerialNumber"));
            params.setMerchantPrivateKey(properties.get("merchantPrivateKey"));
            params.setNotifyUrl(properties.get("notifyUrl"));
            if (StringUtils.isNotBlank(properties.get("wxApiVersion"))) {
                params.setWxApiVersion(properties.get("wxApiVersion").toLowerCase());
            }
        }
        if (StringUtils.isBlank(params.getMerchantId()) || StringUtils.isBlank(params.getAppId())
                || StringUtils.isBlank(params.getNotifyUrl())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信渠道参数不完整，必填键: merchantId/appId/notifyUrl");
        }
        if (API_VERSION_V2.equals(params.getWxApiVersion())) {
            if (StringUtils.isBlank(params.getApiV2Key())) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "微信 V2 模式缺少 apiV2Key（商户支付密钥）");
            }
        } else {
            if (StringUtils.isBlank(params.getApiV3Key()) || StringUtils.isBlank(params.getMerchantSerialNumber())
                    || StringUtils.isBlank(params.getMerchantPrivateKey())) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "微信 V3 模式缺少 apiV3Key/merchantSerialNumber/merchantPrivateKey");
            }
        }
        return params;
    }

    /** 是否 V2 协议。 */
    public boolean isV2() {
        return API_VERSION_V2.equals(wxApiVersion);
    }

    /** 企业付款密钥（缺省回落 V2 支付密钥）。 */
    public String effectiveEntPayKey() {
        return StringUtils.isNotBlank(entPayKey) ? entPayKey : apiV2Key;
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
                && appId.equals(that.appId)
                && wxApiVersion.equals(that.wxApiVersion)
                && java.util.Objects.equals(apiV2Key, that.apiV2Key)
                && java.util.Objects.equals(apiV3Key, that.apiV3Key)
                && java.util.Objects.equals(merchantSerialNumber, that.merchantSerialNumber)
                && java.util.Objects.equals(merchantPrivateKey, that.merchantPrivateKey);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(merchantId, appId, wxApiVersion);
    }
}
