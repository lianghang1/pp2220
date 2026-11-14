package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.ChannelUserHandler;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.net.URLEncoder;
import java.util.Map;

/**
 * 支付宝渠道用户标识处理器：授权页跳转 + auth_code 换取 buyerUserId
 * （alipay.system.oauth.token），供 JSAPI（buyerId）下单前置使用。
 * 实现线程安全。
 *
 * @author lianghang
 */
public class AlipayChannelUserHandler implements ChannelUserHandler {

    private static final String AUTHORIZE_URL =
            "https://openauth.alipay.com/oauth2/publicAppAuthorize.htm";

    private final AlipayClientHolder holder;

    public AlipayChannelUserHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public String buildUserRedirectUrl(ChannelConfig config, String redirectUri, String state) {
        AlipayParams params = AlipayClientHolder.paramsOf(config);
        String target = StringUtils.defaultIfBlank(redirectUri, params.getNotifyUrl());
        try {
            return AUTHORIZE_URL
                    + "?app_id=" + params.getAppId()
                    + "&scope=auth_user"
                    + "&redirect_uri=" + URLEncoder.encode(target, "UTF-8")
                    + "&state=" + StringUtils.defaultIfBlank(state, "pay-gateway");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new GatewayException(GatewayErrorCode.SYSTEM_ERROR,
                    "授权跳转地址编码失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getChannelUserId(ChannelConfig config, Map<String, String> callbackParams) {
        String authCode = callbackParams == null ? null : callbackParams.get("auth_code");
        if (StringUtils.isBlank(authCode)) {
            authCode = callbackParams == null ? null : callbackParams.get("code");
        }
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED,
                    "支付宝授权回跳缺少 auth_code/code 参数");
        }
        AlipaySystemOauthTokenRequest request = new AlipaySystemOauthTokenRequest();
        request.setGrantType("authorization_code");
        request.setCode(authCode);
        AlipaySystemOauthTokenResponse response =
                holder.execute("换取用户标识", config, (client, authToken) -> {
            // 授权码换令牌本身即 ISV 授权流程，无需再携带令牌
            return client.execute(request);
        });
        if (StringUtils.isBlank(response.getUserId())) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝授权令牌响应缺少 userId");
        }
        return response.getUserId();
    }
}
