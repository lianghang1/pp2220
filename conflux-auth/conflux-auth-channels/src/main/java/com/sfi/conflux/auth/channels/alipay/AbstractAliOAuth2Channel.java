package com.sfi.conflux.auth.channels.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝「网页系登录」公共基类（电脑网站 / 手机 H5 端 outer / H5 端内链接 / APP）。
 *
 * <p>支付宝不是标准 RESTful OAuth，而是网关协议：授权页 {@code openauth.alipay.com/oauth2/publicAppAuthorize.htm}
 * → 回调 auth_code → {@code alipay.system.oauth.token} 换 access_token+user_id/open_id →
 * {@code alipay.user.info.share} 拿昵称头像。所有 method 调用与 RSA2 签名/验签交给官方 SDK。</p>
 *
 * <p>用户 id 映射：<b>open_id 优先、user_id 兜底</b>——官方明确 user_id 与 open_id 不兼容，
 * 升级为 open_id 的应用只返回 open_id，故两者都要兼容（JustAuth 公钥/证书模式各自只取其一，本门面统一兜底）。</p>
 *
 * <p>state 校验沿用基类默认（true）——支付宝授权跳转原样回传 state。</p>
 *
 * @author lianghang
 */
abstract class AbstractAliOAuth2Channel extends AbstractAuthChannel {

    protected final AliClientHolder holder;

    AbstractAliOAuth2Channel(AliClientHolder holder) {
        this.holder = holder;
    }

    /** 授权 scope 默认值，各形态可覆盖；config/option 可再覆盖 */
    protected String defaultScope() {
        return "auth_user";
    }

    /** 授权入口：浏览器跳转 / 唤起支付宝的 URL（APP 形态由子类覆盖为 JSAPI） */
    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = AliSupport.firstNonBlank(
                AliSupport.param(config, option, "scope"), config.getScope(), defaultScope());
        StringBuilder url = new StringBuilder(AliSupport.AUTHORIZE_URL)
                .append("?app_id=").append(AliSupport.enc(config.getClientId()))
                .append("&redirect_uri=").append(AliSupport.enc(config.getRedirectUri()))
                .append("&state=").append(AliSupport.enc(state));
        if (scope != null && !scope.trim().isEmpty()) {
            url.append("&scope=").append(AliSupport.enc(scope));
        }
        String result = AliSupport.appendExtra(url.toString(), option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(result);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("auth_code", "authCode", "code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "支付宝回调缺少 auth_code");
        }
        try {
            AlipayClient client = holder.client(config);
            boolean cert = AliSupport.certificateMode(config);

            // 1) auth_code -> access_token + user_id/open_id
            AlipaySystemOauthTokenRequest tokenReq = new AlipaySystemOauthTokenRequest();
            tokenReq.setGrantType("authorization_code");
            tokenReq.setCode(code);
            AlipaySystemOauthTokenResponse tokenResp = cert
                    ? client.certificateExecute(tokenReq)
                    : client.execute(tokenReq);
            if (tokenResp == null || !tokenResp.isSuccess()) {
                String msg = tokenResp == null ? "空响应" : firstNonBlank(tokenResp.getSubMsg(), tokenResp.getMsg());
                return AuthResult.fail("ALIPAY_TOKEN_ERROR", "支付宝换取 token 失败: " + (msg == null ? "未知" : msg));
            }
            String accessToken = tokenResp.getAccessToken();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                return AuthResult.fail("ALIPAY_TOKEN_ERROR", "支付宝 token 响应缺 access_token");
            }
            String idFromToken = AliSupport.firstNonBlank(tokenResp.getOpenId(), tokenResp.getUserId());

            // 2) access_token -> user_info（昵称/头像等）
            AlipayUserInfoShareRequest infoReq = new AlipayUserInfoShareRequest();
            AlipayUserInfoShareResponse infoResp = cert
                    ? client.certificateExecute(infoReq, accessToken)
                    : client.execute(infoReq, accessToken);
            if (infoResp == null || !infoResp.isSuccess()) {
                String msg = infoResp == null ? "空响应" : firstNonBlank(infoResp.getSubMsg(), infoResp.getMsg());
                return AuthResult.fail("ALIPAY_USERINFO_ERROR", "支付宝 userinfo 失败: " + (msg == null ? "未知" : msg));
            }
            return AuthResult.ok(buildUser(infoResp, idFromToken));
        } catch (Exception e) {
            return AuthResult.fail("ALIPAY_OAUTH_ERROR", "支付宝登录失败: " + e.getMessage());
        }
    }

    /** 归一为统一身份：open_id 优先 user_id 兜底（token/userinfo 两处 id 都做兼容） */
    protected AuthUser buildUser(AlipayUserInfoShareResponse info, String idFromToken) {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("open_id", info.getOpenId());
        raw.put("user_id", info.getUserId());
        raw.put("nick_name", info.getNickName());
        raw.put("user_name", info.getUserName());
        raw.put("avatar", info.getAvatar());
        raw.put("gender", info.getGender());
        raw.put("province", info.getProvince());
        raw.put("city", info.getCity());

        return new AuthUser()
                .setChannelCode(channelCode())
                .setOpenId(AliSupport.firstNonBlank(info.getOpenId(), info.getUserId(), idFromToken))
                .setUsername(AliSupport.firstNonBlank(info.getUserName(), info.getNickName()))
                .setAvatar(info.getAvatar())
                .setGender(AliSupport.genderOf(info.getGender()))
                .setRawUserInfo(raw);
    }

    static String firstNonBlank(String... values) {
        return AliSupport.firstNonBlank(values);
    }
}
