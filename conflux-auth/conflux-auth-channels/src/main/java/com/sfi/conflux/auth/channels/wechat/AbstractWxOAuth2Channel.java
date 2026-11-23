package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.service.WxOAuth2Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信 OAuth2 系（开放平台扫码、公众号网页授权）公共基类：身份交换链路相同。
 *
 * <p>授权 URL 各形态不同，留给子类；code→token→userinfo→AuthUser 全部委托 wxjava
 * 的 {@link WxOAuth2Service}（网络/JSON/异常包装由 SDK 负责），本类只做归一映射。</p>
 *
 * @author lianghang
 */
abstract class AbstractWxOAuth2Channel extends AbstractAuthChannel {

    protected final WxClientHolder clients;

    AbstractWxOAuth2Channel(WxClientHolder clients) {
        this.clients = clients;
    }

    /** 取票据的参数名，各形态可不同（扫码 code / 网页 code），默认 code */
    protected String ticketParam() {
        return "code";
    }

    @Override
    protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
        String code = request.firstParam(ticketParam());
        if (code == null || code.trim().isEmpty()) {
            return AuthResult.fail("MISSING_CODE", "微信回调缺少 " + ticketParam());
        }
        try {
            WxOAuth2Service oauth2 = clients.mp(config).getOAuth2Service();
            WxOAuth2AccessToken token = oauth2.getAccessToken(code);
            if (token == null) {
                return AuthResult.fail("TOKEN_FAILED", "微信 code 换取 token 失败");
            }
            WxOAuth2UserInfo info = oauth2.getUserInfo(token, null);
            AuthUser user = new AuthUser()
                    .setChannelCode(channelCode())
                    .setOpenId(token.getOpenId())
                    .setUnionId(WxSupport.firstNonBlank(
                            token.getUnionId(), info == null ? null : info.getUnionId()))
                    .setUsername(info == null ? null : info.getNickname())
                    .setAvatar(info == null ? null : info.getHeadImgUrl())
                    .setGender(WxSupport.genderOf(info == null ? null : info.getSex()));
            if (info != null) {
                user.setRawUserInfo(toRaw(info));
            }
            return AuthResult.ok(user);
        } catch (WxErrorException e) {
            return AuthResult.fail("WECHAT_OAUTH_ERROR",
                    "微信授权失败: " + (e.getError() == null ? e.getMessage() : e.getError().getErrorMsg()));
        }
    }

    /** 保留平台原始资料，供业务按需取用（不丢字段） */
    static Map<String, Object> toRaw(WxOAuth2UserInfo info) {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("openid", info.getOpenid());
        raw.put("unionid", info.getUnionId());
        raw.put("nickname", info.getNickname());
        raw.put("headimgurl", info.getHeadImgUrl());
        raw.put("sex", info.getSex());
        raw.put("country", info.getCountry());
        raw.put("province", info.getProvince());
        raw.put("city", info.getCity());
        raw.put("privileges", info.getPrivileges());
        return raw;
    }
}
