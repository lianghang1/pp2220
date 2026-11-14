package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.ChannelUserHandler;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import okhttp3.OkHttpClient;


import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信渠道用户标识处理器：公众号/小程序网页授权（OAuth2 snsapi_base）
 * 获取付款用户 openid——JSAPI 下单的前置环节。
 *
 * <p>需要渠道参数配置 {@code appSecret}（AppSecret 仅此能力使用，支付协议本身不需要）；
 * 授权域名固定 open.weixin.qq.com，令牌接口固定 api.weixin.qq.com。
 * 实现线程安全。</p>
 *
 * @author lianghang
 */
public class WechatChannelUserHandler implements ChannelUserHandler {

    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String buildUserRedirectUrl(ChannelConfig config, String redirectUri, String state) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        String target = StringUtils.defaultIfBlank(redirectUri, params.getNotifyUrl());
        try {
            return AUTHORIZE_URL
                    + "?appid=" + params.getAppId()
                    + "&redirect_uri=" + java.net.URLEncoder.encode(target, "UTF-8")
                    + "&response_type=code"
                    + "&scope=snsapi_base"
                    + "&state=" + StringUtils.defaultIfBlank(state, "pay-gateway")
                    + "#wechat_redirect";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new GatewayException(GatewayErrorCode.SYSTEM_ERROR,
                    "授权跳转地址编码失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getChannelUserId(ChannelConfig config, Map<String, String> callbackParams) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        if (StringUtils.isBlank(params.getAppSecret())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信用户标识获取需配置 appSecret");
        }
        String code = callbackParams == null ? null : callbackParams.get("code");
        if (StringUtils.isBlank(code)) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED,
                    "微信授权回跳缺少 code 参数");
        }
        String url = ACCESS_TOKEN_URL
                + "?appid=" + params.getAppId()
                + "&secret=" + params.getAppSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";
        try (okhttp3.Response response = httpClient.newCall(
                new okhttp3.Request.Builder().url(url).get().build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            Map<String, Object> result = JsonUtils.toMap(body);
            String openid = result.get("openid") == null ? null : String.valueOf(result.get("openid"));
            if (StringUtils.isBlank(openid)) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "微信换取 openid 失败: {}", body);
            }
            return openid;
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信换取 openid 异常: {}", e.getMessage(), e);
        }
    }
}
