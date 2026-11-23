package com.sfi.conflux.auth.channels.feishu;

/**
 * 飞书应用内网页免登：channelCode = feishu_inapp。
 *
 * <p>飞书客户端内打开网页，网页授权免用户确认直接跳转带 code；后端身份交换与网页授权同构，
 * 共用 {@link AbstractFeishuOAuth2Channel} 的 code→v3 token→user_info 链路。</p>
 *
 * @author lianghang
 */
public class FeishuInAppAuthChannel extends AbstractFeishuOAuth2Channel {

    public static final String CHANNEL_CODE = "feishu_inapp";

    public FeishuInAppAuthChannel() {
        this(new FeishuDefaultHttpClient());
    }

    public FeishuInAppAuthChannel(FeishuHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
