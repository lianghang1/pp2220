package com.sfi.conflux.auth.channels.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书小程序 / 小组件登录：channelCode = feishu_mini。
 *
 * <p>这条链路与 OAuth 授权码系完全不同，必须独立实现（这是最容易出错的地方）：
 * 前端 tt.login / tt.requestAccess 拿 code → 后端先用 app_id+app_secret 换 app_access_token
 * ({@code auth/v3/app_access_token/internal}) → 再 POST {@code mina/v2/tokenLoginValidate}
 * (Bearer app_access_token + code) → 返回 open_id / employee_id / user_access_token。</p>
 *
 * <p>官方明确：该接口返回的 union_id 已废弃、与开放平台 UnionID 不是一个概念，
 * 故本渠道不映射 unionId。小程序容器内免登不回传 state，故关闭 state 校验，
 * 授权入口返回前端需调用的 JSAPI 参数。</p>
 *
 * @author lianghang
 */
public class FeishuMiniAuthChannel extends AbstractAuthChannel {

    public static final String CHANNEL_CODE = "feishu_mini";

    protected final FeishuHttpClient http;

    public FeishuMiniAuthChannel() {
        this(new FeishuDefaultHttpClient());
    }

    public FeishuMiniAuthChannel(FeishuHttpClient http) {
        this.http = http;
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public boolean requireStateCheck() {
        return false;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        Map<String, Object> jsApi = new LinkedHashMap<String, Object>();
        jsApi.put("action", "tt.requestAccess");
        jsApi.put("clientId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("code", "authCode");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "飞书小程序免登缺少 code");
        }
        try {
            // 1) app_id + app_secret -> app_access_token
            String appToken = appAccessToken(config);
            if (appToken == null) {
                return AuthResult.fail("FEISHU_APP_TOKEN_ERROR", "飞书小程序换取 app_access_token 失败");
            }

            // 2) Bearer app_access_token + code -> code2session（独立于 OAuth 链路）
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("Authorization", "Bearer " + appToken);
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("code", code);

            JsonNode resp = JsonUtils.readTree(
                    http.postJson(FeishuSupport.MINI_TOKEN_URL, headers, JsonUtils.toJson(body)));
            JsonNode payload = AbstractFeishuOAuth2Channel.payload(resp);
            if (payload == null || payload.isNull()) {
                return AuthResult.fail("FEISHU_MINI_ERROR", "飞书小程序 userinfo 空响应");
            }
            String bizCode = AbstractFeishuOAuth2Channel.text(resp, "code");
            if (bizCode != null && !"0".equals(bizCode)) {
                String msg = AbstractFeishuOAuth2Channel.firstNonBlank(
                        AbstractFeishuOAuth2Channel.text(resp, "msg"),
                        AbstractFeishuOAuth2Channel.text(resp, "message"));
                return AuthResult.fail("FEISHU_MINI_ERROR",
                        "飞书小程序免登失败: " + (msg == null ? bizCode : msg));
            }

            String openId = AbstractFeishuOAuth2Channel.text(payload, "open_id");
            // 官方：employee_id 才是 user_id；union_id 已废弃，不映射
            String userId = AbstractFeishuOAuth2Channel.text(payload, "employee_id");
            if (openId == null && userId == null) {
                return AuthResult.fail("FEISHU_MINI_ERROR", "飞书小程序响应缺 open_id/employee_id");
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(AbstractFeishuOAuth2Channel.firstNonBlank(openId, userId))
                    .setUsername(userId)
                    .setRawUserInfo(JsonUtils.toMap(payload));
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("FEISHU_MINI_ERROR", "飞书小程序免登失败: " + e.getMessage());
        }
    }

    /** app_id + app_secret 换自建应用 app_access_token（内部接口） */
    private String appAccessToken(AuthConfig config) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("app_id", config.getClientId());
        body.put("app_secret", config.getClientSecret());
        JsonNode resp = JsonUtils.readTree(
                http.postJson(FeishuSupport.APP_TOKEN_URL, null, JsonUtils.toJson(body)));
        if (resp == null || resp.isNull()) {
            return null;
        }
        String code = AbstractFeishuOAuth2Channel.text(resp, "code");
        if (code != null && !"0".equals(code)) {
            return null;
        }
        return AbstractFeishuOAuth2Channel.firstNonBlank(
                AbstractFeishuOAuth2Channel.text(resp, "app_access_token"),
                AbstractFeishuOAuth2Channel.text(resp, "app_access_token"));
    }
}
