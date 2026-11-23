package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 微信开放平台扫码登录（网站应用）：channelCode = wechat_open。
 *
 * <p>授权入口是固定的公开 URL 格式 {@code open.weixin.qq.com/connect/qrconnect}，
 * 由本类按官方参数拼装（wxjava 的 mp 模块只提供 connect/oauth2/authorize，是公众号端点，
 * 不能用于扫码；open 模块的扫码 service 又绑定第三方平台 component 凭证，故此处按公开端点拼接）。
 * 票据交换 code→token→userinfo 全部委托 wxjava，见 {@link AbstractWxOAuth2Channel}。</p>
 *
 * @author lianghang
 */
public class WechatOpenAuthChannel extends AbstractWxOAuth2Channel {

    public static final String CHANNEL_CODE = "wechat_open";
    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String DEFAULT_SCOPE = "snsapi_login";

    public WechatOpenAuthChannel() {
        this(new WxClientHolder());
    }

    WechatOpenAuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = WxSupport.firstNonBlank(config.getScope(), DEFAULT_SCOPE);
        String url = AUTHORIZE_URL
                + "?appid=" + WxSupport.enc(config.getClientId())
                + "&redirect_uri=" + WxSupport.enc(config.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + WxSupport.enc(state)
                + "#wechat_redirect";
        url = WxSupport.appendExtra(url, option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(url);
    }
}
