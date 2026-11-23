package com.sfi.conflux.auth.channels.dingtalk;

import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 钉钉网页内账号密码登录（登录第三方网站）：channelCode = dingtalk_web。
 *
 * <p>与扫码形态共用 OAuth2 链路，差异在授权页呈现（账号密码 / 扫码由钉钉登录页自行决定），
 * 默认 scope 含 openid corpid，便于拿到用户所属组织信息。</p>
 *
 * @author lianghang
 */
public class DingWebAuthChannel extends AbstractDingOAuth2Channel {

    public static final String CHANNEL_CODE = "dingtalk_web";

    public DingWebAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingWebAuthChannel(DingHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    protected String defaultScope() {
        return "openid corpid";
    }
}
