package com.sfi.conflux.auth.channels.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
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
 * 支付宝小程序登录：channelCode = alipay_mini。
 *
 * <p>前端 {@code my.getAuthCode} 拿 authCode 传给后端 —— 不回传 state，故关闭 state 校验，
 * 授权入口返回前端需调用的 JSAPI 参数。</p>
 *
 * <p><b>关键差异（官方明确）</b>：小程序必须通过 {@code alipay.system.oauth.token} 获取
 * user_id/open_id，<b>不支持调用 alipay.user.info.share</b>。故本渠道只做 token 换取，
 * 昵称/头像留空（AuthUser 设计已容忍这些字段为空），原始字段保留在 rawUserInfo。</p>
 *
 * @author lianghang
 */
public class AliMiniAuthChannel extends AbstractAuthChannel {

    public static final String CHANNEL_CODE = "alipay_mini";

    protected final AliClientHolder holder;

    public AliMiniAuthChannel() {
        this(new AliClientHolder());
    }

    public AliMiniAuthChannel(AliClientHolder holder) {
        this.holder = holder;
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
        jsApi.put("action", "my.getAuthCode");
        jsApi.put("clientId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("authCode", "auth_code", "code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "支付宝小程序免登缺少 authCode");
        }
        try {
            AlipayClient client = holder.client(config);
            boolean cert = AliSupport.certificateMode(config);
            AlipaySystemOauthTokenRequest tokenReq = new AlipaySystemOauthTokenRequest();
            tokenReq.setGrantType("authorization_code");
            tokenReq.setCode(code);
            AlipaySystemOauthTokenResponse tokenResp = cert
                    ? client.certificateExecute(tokenReq)
                    : client.execute(tokenReq);
            if (tokenResp == null || !tokenResp.isSuccess()) {
                String msg = tokenResp == null ? "空响应" : firstNonBlank(tokenResp.getSubMsg(), tokenResp.getMsg());
                return AuthResult.fail("ALIPAY_MINI_ERROR", "支付宝小程序换取 token 失败: " + (msg == null ? "未知" : msg));
            }
            // open_id 优先、user_id 兜底（新商户只返回 open_id）
            String openId = AliSupport.firstNonBlank(tokenResp.getOpenId(), tokenResp.getUserId());
            if (openId == null) {
                return AuthResult.fail("ALIPAY_MINI_ERROR", "支付宝小程序响应缺 open_id/user_id");
            }
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            raw.put("open_id", tokenResp.getOpenId());
            raw.put("user_id", tokenResp.getUserId());
            raw.put("union_id", tokenResp.getUnionId());
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(openId)
                    .setRawUserInfo(raw);
            return AuthResult.ok(user);
        } catch (Exception e) {
            return AuthResult.fail("ALIPAY_MINI_ERROR", "支付宝小程序登录失败: " + e.getMessage());
        }
    }

    static String firstNonBlank(String... values) {
        return AliSupport.firstNonBlank(values);
    }
}
