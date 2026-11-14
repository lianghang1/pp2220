package io.github.lianghang1.paygateway.channels.paypal;

import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * PayPal 渠道参数（REST API v2，client credentials 认证）。
 *
 * <p>声明式配置键（attrs 渠道个性键；notifyUrl/sandbox 为统一键由 ChannelConfig 承载）：
 * {@code clientId}/{@code clientSecret}（应用凭据）、{@code webhookId}（Webhook 订阅 ID，
 * 回调验签用）。回调配置反查以 clientId 为渠道侧应用标识；Webhook 事件需在
 * PayPal 开发者平台订阅并配置回调地址。</p>
 *
 * @author lianghang
 */
@Data
public class PayPalParams implements ChannelParams {

    /** 生产 API 网关 */
    public static final String PROD_GATEWAY = "https://api-m.paypal.com";
    /** 沙箱 API 网关 */
    public static final String SANDBOX_GATEWAY = "https://api-m.sandbox.paypal.com";

    /** 应用 Client ID */
    private String clientId;
    /** 应用 Client Secret */
    private String clientSecret;
    /** Webhook 订阅 ID（回调验签用，可空则跳过验签） */
    private String webhookId;

    /** 从配置键值对构建（attrs 渠道个性键）；必填项缺失抛参数异常。 */
    public static PayPalParams fromMap(Map<String, String> properties) {
        PayPalParams params = new PayPalParams();
        if (properties != null) {
            params.setClientId(properties.get("clientId"));
            params.setClientSecret(properties.get("clientSecret"));
            params.setWebhookId(properties.get("webhookId"));
        }
        if (StringUtils.isBlank(params.getClientId()) || StringUtils.isBlank(params.getClientSecret())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "PayPal 渠道参数不完整，必填键: clientId/clientSecret");
        }
        return params;
    }

    /** API 网关地址（按沙箱开关切换；开关由 ChannelConfig 统一键承载）。 */
    public String gateway(boolean sandbox) {
        return sandbox ? SANDBOX_GATEWAY : PROD_GATEWAY;
    }

    @Override
    public String channelAppId() {
        return clientId;
    }

    /** 关键凭证字段参与相等性判断（客户端缓存复用的依据）。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PayPalParams)) {
            return false;
        }
        PayPalParams that = (PayPalParams) other;
        return clientId.equals(that.clientId)
                && clientSecret.equals(that.clientSecret);
    }

    @Override
    public int hashCode() {
        return clientId.hashCode();
    }
}
