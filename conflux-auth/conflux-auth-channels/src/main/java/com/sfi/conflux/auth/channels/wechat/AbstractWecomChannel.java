package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;
import me.chanjar.weixin.cp.bean.WxCpUserDetail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信系 provider 公共基类：身份交换统一委托 wxjava cp 模块。
 *
 * <p>授权 URL 各形态不同（扫码 / 网页授权 / 新版 Web 登录 / 第三方），留给子类拼装；
 * 票据 code→userid/openid→（可选）userdetail 全部走 {@link WxCpOAuth2Service}。
 * 企微 OAuth2 只返回 userId/openId，昵称头像需 snsapi_privateinfo 才有 userTicket，
 * 故 userDetail 作为可选增强：有 userTicket 才调用，失败不影响主身份（字段容忍为空）。</p>
 *
 * @author lianghang
 */
abstract class AbstractWecomChannel extends AbstractAuthChannel {

    protected final WxClientHolder clients;

    AbstractWecomChannel(WxClientHolder clients) {
        this.clients = clients;
    }

    /** 取 code 的参数名：企微 OAuth2 回调统一叫 code */
    protected String ticketParam() {
        return "code";
    }

    /**
     * 用 code 换 userInfo 的策略：企业自建应用网页授权用 getUserInfo，
     * 扫码/第三方登录用 getAuthUserInfo。子类按需覆盖。
     */
    protected WxCpOauth2UserInfo fetchUserInfo(WxCpOAuth2Service oauth2, AuthConfig config, String code)
            throws WxErrorException {
        Integer agentId = WxSupport.agentId(config);
        if (agentId != null) {
            return oauth2.getUserInfo(agentId, code);
        }
        return oauth2.getUserInfo(code);
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam(ticketParam());
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "企微回调缺少 " + ticketParam());
        }
        try {
            WxCpOAuth2Service oauth2 = clients.cp(config).getOauth2Service();
            WxCpOauth2UserInfo info = fetchUserInfo(oauth2, config, code);
            if (info == null) {
                return AuthResult.fail("USERINFO_FAILED", "企微 code 换取用户身份失败");
            }
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(WxSupport.firstNonBlank(info.getOpenId(), info.getUserId()))
                    .setUsername(info.getUserId());
            Map<String, Object> raw = new LinkedHashMap<String, Object>();
            raw.put("userId", info.getUserId());
            raw.put("openId", info.getOpenId());
            raw.put("externalUserId", info.getExternalUserId());
            raw.put("deviceId", info.getDeviceId());
            user.setRawUserInfo(raw);

            // 可选增强：有 userTicket 才查详细信息（昵称/头像/邮箱），失败不影响主身份
            String userTicket = info.getUserTicket();
            if (userTicket != null && !userTicket.trim().isEmpty()) {
                try {
                    WxCpUserDetail detail = oauth2.getUserDetail(userTicket);
                    if (detail != null) {
                        user.setUsername(WxSupport.firstNonBlank(detail.getName(), user.getUsername()));
                        user.setAvatar(detail.getAvatar());
                        user.setEmail(detail.getEmail());
                        user.setGender(WxSupport.genderOf(detail.getGender()));
                        raw.put("userDetail", detail);
                    }
                } catch (Exception ignore) {
                    // userDetail 是增强项，降级保留 userId/openId，不否决登录结果
                }
            }
            return AuthResult.ok(user);
        } catch (WxErrorException e) {
            return AuthResult.fail("WECOM_OAUTH_ERROR",
                    "企业微信授权失败: " + (e.getError() == null ? e.getMessage() : e.getError().getErrorMsg()));
        }
    }
}
