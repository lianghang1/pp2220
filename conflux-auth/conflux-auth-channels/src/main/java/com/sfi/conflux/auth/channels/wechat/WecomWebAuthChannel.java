package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 企业微信网页授权（企业内 H5）：channelCode = wechat_wecom_web。
 *
 * <p>授权入口委托 wxjava cp 模块 {@code buildAuthorizationUrl}，生成
 * connect/oauth2/authorize（appid=CorpID）；身份交换用 getUserInfo。
 * scope 默认 snsapi_base（静默，拿 userid），可配置为 snsapi_privateinfo（手动授权，含敏感信息）。</p>
 *
 * @author lianghang
 */
public class WecomWebAuthChannel extends AbstractWecomChannel {

    public static final String CHANNEL_CODE = "wechat_wecom_web";
    private static final String DEFAULT_SCOPE = "snsapi_base";

    public WecomWebAuthChannel() {
        this(new WxClientHolder());
    }

    WecomWebAuthChannel(WxClientHolder clients) {
        super(clients);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        String scope = WxSupport.firstNonBlank(config.getScope(), DEFAULT_SCOPE);
        String url = clients.cp(config).getOauth2Service()
                .buildAuthorizationUrl(config.getRedirectUri(), state, scope);
        url = WxSupport.appendExtra(url, option == null ? null : option.getExtraParams());
        return new AuthorizeResult().setAuthorizeUrl(url);
    }
}
