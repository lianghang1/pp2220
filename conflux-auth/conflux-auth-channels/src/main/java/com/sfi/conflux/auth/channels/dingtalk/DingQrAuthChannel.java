package com.sfi.conflux.auth.channels.dingtalk;

import com.sfi.conflux.auth.config.AuthConfig;

/**
 * 钉钉扫码登录（登录第三方网站）：channelCode = dingtalk_qr。
 *
 * <p>浏览器跳转型：授权入口 login.dingtalk.com/oauth2/auth，用户扫码确认后回调 authCode，
 * 身份交换见 {@link AbstractDingOAuth2Channel}。默认 scope=openid。</p>
 *
 * @author lianghang
 */
public class DingQrAuthChannel extends AbstractDingOAuth2Channel {

    public static final String CHANNEL_CODE = "dingtalk_qr";

    public DingQrAuthChannel() {
        this(new DingDefaultHttpClient());
    }

    public DingQrAuthChannel(DingHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
