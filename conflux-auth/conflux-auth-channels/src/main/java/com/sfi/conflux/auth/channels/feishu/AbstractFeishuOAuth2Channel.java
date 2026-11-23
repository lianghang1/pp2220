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
 * 飞书「OAuth 授权码登录」系公共基类（网页 / 扫码 / 客户端内网页 / 移动端 App 四种形态）。
 *
 * <p>链路（官方 RFC6749，遵循 JustAuth 骨架但端点用最新 v3）：
 * 授权页 {@code accounts.feishu.cn/open-apis/authen/v1/authorize} → 回调 code →
 * POST token 端点（默认 v3，可配置 v2）换 user_access_token →
 * GET {@code authen/v1/user_info}（Bearer token）拿 open_id/union_id/name/avatar。</p>
 *
 * <p>state 校验沿用基类默认（true）——授权跳转型平台原样回传 state。</p>
 *
 * <p>PKCE 可选：使用方通过 attr/extraParams 注入 {@code code_verifier}（换 token 时带上）、
 * 授权阶段把 {@code code_challenge}/{@code code_challenge_method} 放进 extraParams 即可，
 * 框架不生成、不写死。</p>
 *
 * @author lianghang
 */
abstract class AbstractFeishuOAuth2Channel extends AbstractAuthChannel {

    protected final FeishuHttpClient http;

    AbstractFeishuOAuth2Channel(FeishuHttpClient http) {
        this.http = http;
    }

    /** 授权 scope 默认值，各形态可覆盖；调用方 config/option 可再覆盖 */
    protected String defaultScope() {
        return null;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = FeishuSupport.firstNonBlank(
                FeishuSupport.param(config, option, "scope"),
                config.getScope(), defaultScope());
        StringBuilder url = new StringBuilder(FeishuSupport.AUTHORIZE_URL)
                .append("?app_id=").append(FeishuSupport.enc(config.getClientId()))
                .append("&redirect_uri=").append(FeishuSupport.enc(config.getRedirectUri()))
                .append("&response_type=code")
                .append("&state=").append(FeishuSupport.enc(state));
        if (scope != null && !scope.trim().isEmpty()) {
            url.append("&scope=").append(FeishuSupport.enc(scope));
        }
        String result = FeishuSupport.appendExtra(url.toString(), option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(result);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("code", "auth_code", "authCode");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "飞书回调缺少 code");
        }
        try {
            // 1) code -> user_access_token（v3/v2 端点可配置）
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("grant_type", "authorization_code");
            body.put("client_id", config.getClientId());
            body.put("client_secret", config.getClientSecret());
            body.put("code", code);
            String redirectUri = config.getRedirectUri();
            if (redirectUri != null && !redirectUri.trim().isEmpty()) {
                body.put("redirect_uri", redirectUri);
            }
            // PKCE：使用方注入 code_verifier 才带，框架不生成
            String codeVerifier = FeishuSupport.param(config, null, "code_verifier");
            if (codeVerifier != null && !codeVerifier.trim().isEmpty()) {
                body.put("code_verifier", codeVerifier);
            }

            String tokenUrl = FeishuSupport.tokenUrl(config, null);
            JsonNode tokenResp = JsonUtils.readTree(http.postJson(tokenUrl, null, JsonUtils.toJson(body)));
            JsonNode token = payload(tokenResp);
            if (token == null || token.isNull()) {
                return AuthResult.fail("FEISHU_TOKEN_ERROR", "飞书换取 token 空响应");
            }
            String bizCode = text(token, "code");
            if (bizCode != null && !"0".equals(bizCode)) {
                String msg = firstNonBlank(text(token, "error_description"), text(token, "msg"), text(token, "message"));
                return AuthResult.fail("FEISHU_TOKEN_ERROR", "飞书换取 token 失败: " + (msg == null ? bizCode : msg));
            }
            String accessToken = firstNonBlank(text(token, "access_token"), text(token, "user_access_token"));
            if (accessToken == null) {
                return AuthResult.fail("FEISHU_TOKEN_ERROR", "飞书 token 响应缺 access_token");
            }

            // 2) user_access_token -> user_info（注意 v2/v3 token 响应是顶层字段，user_info 是 data 包裹）
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("Authorization", "Bearer " + accessToken);
            JsonNode infoResp = JsonUtils.readTree(http.get(FeishuSupport.USERINFO_URL, headers));
            JsonNode info = payload(infoResp);
            if (info == null || info.isNull()) {
                return AuthResult.fail("FEISHU_USERINFO_ERROR", "飞书 userinfo 空响应");
            }
            String infoCode = text(info, "code");
            if (infoCode != null && !"0".equals(infoCode)) {
                String msg = firstNonBlank(text(info, "msg"), text(info, "message"));
                return AuthResult.fail("FEISHU_USERINFO_ERROR", "飞书 userinfo 失败: " + (msg == null ? infoCode : msg));
            }
            return AuthResult.ok(buildUser(info));
        } catch (Exception e) {
            return AuthResult.fail("FEISHU_OAUTH_ERROR", "飞书 OAuth 失败: " + e.getMessage());
        }
    }

    /** 归一为统一身份：open_id / union_id / user_id → openId/unionId，name/avatar_url/email */
    protected AuthUser buildUser(JsonNode info) {
        AuthUser user = new AuthUser()
                .setChannelCode(channelCode())
                .setOpenId(firstNonBlank(text(info, "open_id"), text(info, "user_id")))
                .setUnionId(text(info, "union_id"))
                .setUsername(firstNonBlank(text(info, "name"), text(info, "en_name")))
                .setAvatar(firstNonBlank(text(info, "avatar_url"), text(info, "avatar_big")))
                .setEmail(firstNonBlank(text(info, "email"), text(info, "enterprise_email")))
                .setGender(FeishuSupport.genderOf(text(info, "gender")))
                .setRawUserInfo(JsonUtils.toMap(info));
        return user;
    }

    /**
     * 取响应载荷：飞书 token 端点是顶层字段，user_info 是 {@code data} 包裹；
     * 统一处理两种形态，兼容历史响应结构。
     */
    static JsonNode payload(JsonNode resp) {
        if (resp == null || resp.isNull()) {
            return null;
        }
        JsonNode data = resp.get("data");
        return (data != null && !data.isNull()) ? data : resp;
    }

    /** 安全取文本字段：不存在 / null / NullNode 返回 null */
    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    static String firstNonBlank(String... values) {
        return FeishuSupport.firstNonBlank(values);
    }
}
