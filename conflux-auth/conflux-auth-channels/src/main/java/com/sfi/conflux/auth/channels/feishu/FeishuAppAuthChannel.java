package com.sfi.conflux.auth.channels.feishu;

/**
 * 飞书移动应用登录（iOS/Android/鸿蒙）：channelCode = feishu_app。
 *
 * <p>移动端用飞书登录 SDK 拉起授权拿 code，回传后端；后端身份交换与网页授权同构，
 * 共用 {@link AbstractFeishuOAuth2Channel}。移动端 SDK 属前端集成，不在本门面范围。</p>
 *
 * @author lianghang
 */
public class FeishuAppAuthChannel extends AbstractFeishuOAuth2Channel {

    public static final String CHANNEL_CODE = "feishu_app";

    public FeishuAppAuthChannel() {
        this(new FeishuDefaultHttpClient());
    }

    public FeishuAppAuthChannel(FeishuHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
