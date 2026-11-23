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
 * 钉钉第三方企业应用免登：channelCode = dingtalk_h5_isv。
 *
 * <p>前端 JSAPI dd.requestAuthCode（带 corpId）拿免登 code；身份交换走授权企业的
 * access_token（clientId 传 SuiteKey、clientSecret 传 SuiteSecret）+ /topapi/v2/user/getuserinfo，
 * 与企业内部 H5 同构。第三方企业特有参数（如 corpId）由 attr / extraParams 注入，不写死。</p>
 *
 * @author lianghang
 */
public class DingH5IsvAuthChannel extends AbstractDingFreeLoginChannel {

    public static final String CHANNEL_CODE = "dingtalk_h5_isv";

    public DingH5IsvAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingH5IsvAuthChannel(DingHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected String jsApiAction() {
        return "dd.requestAuthCode";
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("code", "authCode");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "钉钉第三方免登缺少 code");
        }
        String accessToken = appAccessToken(config);
        if (accessToken == null) {
            return AuthResult.fail("DING_TOKEN_ERROR", "钉钉第三方 gettoken 失败");
        }
        try {
            String url = "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo"
                    + "?access_token=" + DingSupport.enc(accessToken);
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("code", code);
            JsonNode resp = JsonUtils.readTree(http.postJson(url, null, JsonUtils.toJson(body)));
            if (resp == null) {
                return AuthResult.fail("DING_USERINFO_ERROR", "钉钉第三方免登 userinfo 空响应");
            }
            if (resp.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(resp, "errcode"))) {
                return AuthResult.fail("DING_USERINFO_ERROR",
                        "钉钉第三方免登失败: " + resp.path("errmsg").asText());
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
            return AuthResult.fail("DING_FREELOGIN_ERROR", "钉钉第三方免登失败: " + e.getMessage());
        }
    }
}
