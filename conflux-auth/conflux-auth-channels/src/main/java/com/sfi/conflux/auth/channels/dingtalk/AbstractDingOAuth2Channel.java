package com.sfi.conflux.auth.channels.dingtalk;

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
 * 钉钉「登录第三方网站」OAuth2 系公共基类（扫码 dingtalk_qr / 网页 dingtalk_web）。
 *
 * <p>授权入口：{@code login.dingtalk.com/oauth2/auth}（公开端点，按官方参数自拼）；
 * 票据交换：code → POST {@code /v1.0/oauth2/userAccessToken} 换 accessToken，
 * 再 GET {@code /v1.0/contact/users/me} 拿 userinfo，均走标准 OAuth2 + JSON，
 * 由 {@link DingHttpClient} 委托 HTTP 传输，本类只做协议拼装与字段归一。</p>
 *
 * <p>state 校验沿用基类默认（true）——跳转型授权平台会原样回传 state。</p>
 *
 * @author lianghang
 */
abstract class AbstractDingOAuth2Channel extends AbstractAuthChannel {

    static final String AUTHORIZE_URL = "https://login.dingtalk.com/oauth2/auth";
    static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    static final String USERINFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    protected final DingHttpClient http;

    AbstractDingOAuth2Channel(DingHttpClient http) {
        this.http = http;
    }

    /** 各形态默认 scope：扫码 openid；网页可含 corpid。都允许 config/option 覆盖 */
    protected String defaultScope() {
        return "openid";
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = DingSupport.firstNonBlank(
                option == null ? null : DingSupport.param(config, option, "scope"),
                config.getScope(), defaultScope());
        String prompt = DingSupport.firstNonBlank(
                DingSupport.param(config, option, "prompt"), "consent");
        String url = AUTHORIZE_URL
                + "?redirect_uri=" + DingSupport.enc(config.getRedirectUri())
                + "&response_type=code"
                + "&client_id=" + DingSupport.enc(config.getClientId())
                + "&scope=" + DingSupport.enc(scope)
                + "&prompt=" + DingSupport.enc(prompt)
                + "&state=" + DingSupport.enc(state);
        url = DingSupport.appendExtra(url, option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(url);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("authCode", "code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "钉钉回调缺少 authCode/code");
        }
        try {
            Map<String, String> tokenBody = new LinkedHashMap<String, String>();
            tokenBody.put("clientId", config.getClientId());
            tokenBody.put("clientSecret", config.getClientSecret());
            tokenBody.put("code", code);
            tokenBody.put("grantType", "authorization_code");

            String tokenJson = http.postJson(TOKEN_URL, null, JsonUtils.toJson(tokenBody));
            JsonNode tokenNode = JsonUtils.readTree(tokenJson);
            JsonNode err = tokenNode == null ? null : tokenNode.get("errorCode");
            if (tokenNode == null || (err != null && !err.isNull() && !"0".equals(err.asText()))) {
                String msg = tokenNode == null ? "空响应" : tokenNode.path("errorMsg").asText("换取 token 失败");
                return AuthResult.fail("DING_TOKEN_ERROR", "钉钉换取 token 失败: " + msg);
            }
            String accessToken = text(tokenNode, "accessToken");
            if (accessToken == null) {
                return AuthResult.fail("DING_TOKEN_ERROR", "钉钉 token 响应缺 accessToken");
            }

            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("x-acs-dingtalk-access-token", accessToken);
            String infoJson = http.get(USERINFO_URL, headers);
            JsonNode info = JsonUtils.readTree(infoJson);
            if (info == null || info.isNull()) {
                return AuthResult.fail("DING_USERINFO_ERROR", "钉钉 userinfo 空响应");
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(DingSupport.firstNonBlank(text(info, "openId"), text(info, "unionId")))
                    .setUnionId(text(info, "unionId"))
                    .setUsername(text(info, "nick"))
                    .setAvatar(text(info, "avatarUrl"))
                    .setEmail(text(info, "email"))
                    .setGender(DingSupport.genderOf(text(info, "gender")))
                    .setRawUserInfo(JsonUtils.toMap(info));
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("DING_OAUTH_ERROR", "钉钉 OAuth 失败: " + e.getMessage());
        }
    }

    /** 安全取文本字段：不存在 / null / NullNode 返回 null */
    static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}
