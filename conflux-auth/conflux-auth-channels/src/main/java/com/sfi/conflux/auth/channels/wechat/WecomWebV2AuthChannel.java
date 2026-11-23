package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;

/**
 * 企业微信新版 Web 登录（wwlogin，自建应用 CorpApp）：channelCode = wechat_wecom_web_v2。
 *
 * <p>授权入口是公开端点 {@code login.work.weixin.qq.com/wwlogin/sso/login}，
 * login_type=CorpApp、appid=CorpID、agentid 必填；wxjava 未封装该 URL，按官方文档拼装。
 * 身份交换用 getAuthUserInfo。服务商/代开发场景可用 extraParams 覆盖 login_type 与 appid。</p>
 *
 * @author lianghang
 */
public class WecomWebV2AuthChannel extends AbstractWecomChannel {

    public static final String CHANNEL_CODE = "wechat_wecom_web_v2";
    private static final String AUTHORIZE_URL = "https://login.work.weixin.qq.com/wwlogin/sso/login";

    public WecomWebV2AuthChannel() {
        this(new WxClientHolder());
    }

    WecomWebV2AuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
                .append("?login_type=CorpApp")
                .append("&appid=").append(WxSupport.enc(config.getClientId()));
        Integer agentId = WxSupport.agentId(config);
        if (agentId != null) {
            url.append("&agentid=").append(agentId);
        }
        url.append("&redirect_uri=").append(WxSupport.enc(config.getRedirectUri()))
                .append("&state=").append(WxSupport.enc(state));
        String result = WxSupport.appendExtra(url.toString(), option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(result);
    }

    @Override
    protected WxCpOauth2UserInfo fetchUserInfo(WxCpOAuth2Service oauth2, AuthConfig config, String code)
            throws WxErrorException {
        return oauth2.getAuthUserInfo(code);
    }
}
