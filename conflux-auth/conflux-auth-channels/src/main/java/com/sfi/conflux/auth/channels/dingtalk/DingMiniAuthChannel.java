package com.sfi.conflux.auth.channels.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.common.util.JsonUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉小程序免登：channelCode = dingtalk_mini。
 *
 * <p>前端 JSAPI dd.getAuthCode 拿临时授权码 authCode，后端 POST /sns/getuserinfo_bycode，
 * 官方要求 query 带 accessKey + timestamp + HmacSHA256 签名（签名串 = timestamp + "\n" + appSecret），
 * body 带 tmp_auth_code。签名属钉钉协议的一部分，在渠道内用 JDK 标准 Mac 实现，不引官方 SDK。
 * 返回 user_info{nick, openid, unionid}，无标准 token/userinfo 分层。</p>
 *
 * @author lianghang
 */
public class DingMiniAuthChannel extends AbstractDingFreeLoginChannel {

    public static final String CHANNEL_CODE = "dingtalk_mini";
    private static final String USERINFO_URL = "https://oapi.dingtalk.com/sns/getuserinfo_bycode";

    public DingMiniAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingMiniAuthChannel(DingHttpClient http) {
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
        String code = request.firstParam("authCode", "code", "tmp_auth_code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "钉钉小程序免登缺少 authCode");
        }
        try {
            String accessKey = config.getClientId();
            String appSecret = config.getClientSecret();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = hmacSha256(timestamp + "\n" + appSecret, appSecret);

            String url = USERINFO_URL
                    + "?accessKey=" + DingSupport.enc(accessKey)
                    + "&timestamp=" + timestamp
                    + "&signature=" + DingSupport.enc(signature);
            Map<String, String> body = new LinkedHashMap<String, String>();
            body.put("tmp_auth_code", code);

            JsonNode resp = JsonUtils.readTree(http.postJson(url, null, JsonUtils.toJson(body)));
            if (resp == null) {
                return AuthResult.fail("DING_USERINFO_ERROR", "钉钉小程序 userinfo 空响应");
            }
            if (resp.has("errcode") && !"0".equals(AbstractDingOAuth2Channel.text(resp, "errcode"))) {
                return AuthResult.fail("DING_USERINFO_ERROR",
                        "钉钉小程序免登失败: " + resp.path("errmsg").asText());
            }
            JsonNode info = resp.get("user_info");
            if (info == null || info.isNull()) {
                info = resp;
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(AbstractDingOAuth2Channel.text(info, "openid"))
                    .setUnionId(AbstractDingOAuth2Channel.text(info, "unionid"))
                    .setUsername(AbstractDingOAuth2Channel.text(info, "nick"))
                    .setRawUserInfo(JsonUtils.toMap(info));
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("DING_MINI_ERROR", "钉钉小程序免登失败: " + e.getMessage());
        }
    }

    /** HmacSHA256(data, key) → 十六进制小写串（钉钉签名协议） */
    static String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
