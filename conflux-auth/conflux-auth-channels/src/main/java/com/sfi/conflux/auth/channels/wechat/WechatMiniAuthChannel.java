package com.sfi.conflux.auth.channels.wechat;

import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.error.WxErrorException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序登录：channelCode = wechat_mini。
 *
 * <p>JSAPI 形态：授权在前端调用 wx.login 拿到 js_code，由业务传给后端；授权入口不产生 URL，
 * 而是返回提示信息。票据交换走 wxjava miniapp 的 {@code jsCode2SessionInfo}，直接返回
 * openId/unionId/session_key（无 token/userinfo 环节），昵称头像需前端另行授权获取，
 * 本 provider 容忍这些字段为空。验证码不走回调 URL，故关闭 state 校验。</p>
 *
 * @author lianghang
 */
public class WechatMiniAuthChannel extends AbstractAuthChannel {

    public static final String CHANNEL_CODE = "wechat_mini";

    private final WxClientHolder clients;

    public WechatMiniAuthChannel() {
        this(new WxClientHolder());
    }

    WechatMiniAuthChannel(WxClientHolder clients) {
        this.clients = clients;
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    /** 小程序授权在前端完成，无后端回调 URL，state 校验不适用 */
    @Override
    public boolean requireStateCheck() {
        return false;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        Map<String, Object> jsApi = new LinkedHashMap<String, Object>();
        jsApi.put("action", "wx.login");
        jsApi.put("appId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam("js_code", "code");
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "小程序登录缺少 js_code");
        }
        try {
            WxMaJscode2SessionResult session = clients.ma(config).jsCode2SessionInfo(code);
            if (session == null) {
                return AuthResult.fail("SESSION_FAILED", "小程序 js_code 换取 session 失败");
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(CHANNEL_CODE)
                    .setOpenId(session.getOpenid())
                    .setUnionId(session.getUnionid());
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            raw.put("openid", session.getOpenid());
            raw.put("unionid", session.getUnionid());
            raw.put("session_key", session.getSessionKey());
            user.setRawUserInfo(raw);
            return AuthResult.ok(user);
        } catch (WxErrorException e) {
            return AuthResult.fail("WECHAT_MINI_ERROR",
                    "小程序登录失败: " + (e.getError() == null ? e.getMessage() : e.getError().getErrorMsg()));
        }
    }
}
