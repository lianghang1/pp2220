package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;

/**
 * 企业微信扫码登录（独立二维码，网站用）：channelCode = wechat_wecom_qr。
 *
 * <p>授权入口是公开端点 {@code open.work.weixin.qq.com/wwopen/sso/qrConnect}，
 * wxjava cp 模块未封装该 URL 生成，按官方参数拼装；身份交换用 cp 的
 * {@code getAuthUserInfo}（对应 /cgi-bin/auth/getuserinfo，扫码登录场景）。</p>
 *
 * @author lianghang
 */
public class WecomQrAuthChannel extends AbstractWecomChannel {

    public static final String CHANNEL_CODE = "wechat_wecom_qr";
    private static final String AUTHORIZE_URL = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    public WecomQrAuthChannel() {
        this(new WxClientHolder());
    }

    WecomQrAuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        StringBuilder url = new StringBuilder(AUTHORIZE_URL)
                .append("?appid=").append(WxSupport.enc(config.getClientId()));
        Integer agentId = WxSupport.agentId(config);
        if (agentId != null) {
            url.append("&agentid=").append(agentId);
        }
        url.append("&redirect_uri=").append(WxSupport.enc(config.getRedirectUri()))
                .append("&state=").append(WxSupport.enc(state));
        String result = WxSupport.appendExtra(url.toString(), option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(result);
    }

    /** 扫码登录走 getAuthUserInfo（/cgi-bin/auth/getuserinfo） */
    @Override
    protected WxCpOauth2UserInfo fetchUserInfo(WxCpOAuth2Service oauth2, AuthConfig config, String code)
            throws WxErrorException {
        return oauth2.getAuthUserInfo(code);
    }
}
