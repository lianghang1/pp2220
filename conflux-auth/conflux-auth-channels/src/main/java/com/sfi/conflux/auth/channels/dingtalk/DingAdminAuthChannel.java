package com.sfi.conflux.auth.channels.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉应用管理后台免登：channelCode = dingtalk_admin。
 *
 * <p>管理后台打开应用页时 URL 追加免登 code（由钉钉容器注入），后端走
 * sso/gettoken 换 access_token，再 POST /topapi/v2/user/getuserinfo 拿管理员身份。
 * 与管理后台相关的特有参数（corpId 等）由 attr / extraParams 注入。</p>
 *
 * @author lianghang
 */
public class DingAdminAuthChannel extends AbstractDingFreeLoginChannel {

    public static final String CHANNEL_CODE = "dingtalk_admin";

    public DingAdminAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingAdminAuthChannel(DingHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected String jsApiAction() {
        return "dingtalk.admin.sso";
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("code", "authCode", "ssoCode");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "钉钉管理后台免登缺少 code");
        }
        String url = "https://oapi.dingtalk.com/sso/gettoken"
                + "?corpid=" + DingSupport.enc(config.getClientId())
                + "&corpsecret=" + DingSupport.enc(config.getClientSecret());
        JsonNode tokenResp = JsonUtils.readTree(http.get(url, null));
        if (tokenResp == null
                || (tokenResp.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(tokenResp, "errcode")))) {
            return AuthResult.fail("DING_TOKEN_ERROR", "钉钉 sso gettoken 失败");
        }
        String accessToken = AbstractDingOAuth2Channel.text(tokenResp, "access_token");
        if (accessToken == null) {
            return AuthResult.fail("DING_TOKEN_ERROR", "钉钉 sso gettoken 缺 access_token");
        }
        try {
            String infoUrl = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo"
                    + "?access_token=" + DingSupport.enc(accessToken);
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("code", code);
            JsonNode resp = JsonUtils.readTree(http.postJson(infoUrl, null, JsonUtils.toJson(body)));
            if (resp == null) {
                return AuthResult.fail("DING_USERINFO_ERROR", "钉钉管理后台 userinfo 空响应");
            }
            if (resp.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(resp, "errcode"))) {
                return AuthResult.fail("DING_USERINFO_ERROR",
                        "钉钉管理后台免登失败: " + resp.path("errmsg").asText());
            }
            JsonNode result = resp.get("result");
            if (result == null || result.isNull()) {
                result = resp;
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(DingSupport.firstNonBlank(
                            AbstractDingOAuth2Channel.text(result, "userid"),
                            AbstractDingOAuth2Channel.text(result, "userId")))
                    .setRawUserInfo(JsonUtils.toMap(result));
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("DING_ADMIN_ERROR", "钉钉管理后台免登失败: " + e.getMessage());
        }
    }
}
