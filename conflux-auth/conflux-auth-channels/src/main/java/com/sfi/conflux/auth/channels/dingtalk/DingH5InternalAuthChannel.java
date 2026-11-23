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
 * 钉钉企业内部 H5 微应用免登：channelCode = dingtalk_h5_internal。
 *
 * <p>前端 JSAPI dd.getAuthCode 拿免登 code，后端 gettoken 换应用 access_token，
 * 再 POST /topapi/v2/user/getuserinfo（access_token + code）拿 userid，
 * 最后可选 GET /v1.0/contact/users/{unionId} 增强昵称头像（失败不否决）。</p>
 *
 * @author lianghang
 */
public class DingH5InternalAuthChannel extends AbstractDingFreeLoginChannel {

    public static final String CHANNEL_CODE = "dingtalk_h5_internal";

    public DingH5InternalAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingH5InternalAuthChannel(DingHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected String jsApiAction() {
        return "dd.getAuthCode";
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("code", "authCode");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "钉钉免登缺少 code");
        }
        String accessToken = appAccessToken(config);
        if (accessToken == null) {
            return AuthResult.fail("DING_TOKEN_ERROR", "钉钉 gettoken 失败");
        }
        try {
            String url = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo"
                    + "?access_token=" + DingSupport.enc(accessToken);
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("code", code);
            JsonNode resp = JsonUtils.readTree(http.postJson(url, null, JsonUtils.toJson(body)));
            if (resp == null) {
                return AuthResult.fail("DING_USERINFO_ERROR", "钉钉免登 userinfo 空响应");
            }
            if (resp.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(resp, "errcode"))) {
                return AuthResult.fail("DING_USERINFO_ERROR",
                        "钉钉免登失败: " + resp.path("errmsg").asText());
            }
            JsonNode result = resp.get("result");
            if (result == null || result.isNull()) {
                result = resp;
            }
            String userId = AbstractDingOAuth2Channel.text(result, "userid");
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(userId)
                    .setUsername(AbstractDingOAuth2Channel.text(result, "name"))
                    .setRawUserInfo(JsonUtils.toMap(result));
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("DING_FREELOGIN_ERROR", "钉钉免登失败: " + e.getMessage());
        }
    }
}
