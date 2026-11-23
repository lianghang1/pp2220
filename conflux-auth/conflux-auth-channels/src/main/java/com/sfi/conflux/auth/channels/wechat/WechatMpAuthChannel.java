package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 微信公众号网页授权：channelCode = wechat_mp。
 *
 * <p>授权入口用 wxjava mp 模块的 {@code WxOAuth2Service.buildAuthorizationUrl}，
 * 生成 connect/oauth2/authorize（公众号网页授权端点，scope 默认 snsapi_userinfo）；
 * 票据交换同 {@link AbstractWxOAuth2Channel}。</p>
 *
 * @author lianghang
 */
public class WechatMpAuthChannel extends AbstractWxOAuth2Channel {

    public static final String CHANNEL_CODE = "wechat_mp";
    private static final String DEFAULT_SCOPE = "snsapi_userinfo";

    public WechatMpAuthChannel() {
        this(new WxClientHolder());
    }

    WechatMpAuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = WxSupport.firstNonBlank(config.getScope(), DEFAULT_SCOPE);
        String url = clients.mp(config).getOAuth2Service()
                .buildAuthorizationUrl(config.getRedirectUri(), scope, state);
        url = WxSupport.appendExtra(url, option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(url);
    }
}
