package com.sfi.conflux.auth.channels.feishu;

/**
 * 飞书浏览器网页授权登录（第三方网站 OAuth）：channelCode = feishu_web。
 *
 * <p>浏览器跳转授权页 → 回调 code → 共用 {@link AbstractFeishuOAuth2Channel} 的
 * code→v3 token→user_info 链路。scope 可由 config/option 注入。</p>
 *
 * @author lianghang
 */
public class FeishuWebAuthChannel extends AbstractFeishuOAuth2Channel {

    public static final String CHANNEL_CODE = "feishu_web";

    public FeishuWebAuthChannel() {
        this(new FeishuDefaultHttpClient());
    }

    public FeishuWebAuthChannel(FeishuHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
