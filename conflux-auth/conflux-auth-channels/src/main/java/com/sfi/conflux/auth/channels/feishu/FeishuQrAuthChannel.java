package com.sfi.conflux.auth.channels.feishu;

/**
 * 飞书扫码登录：channelCode = feishu_qr。
 *
 * <p>前端用飞书 QR SDK 渲染 {@link #doBuildAuthorize} 生成的授权 URL，扫码确认后
 * 回调 code；后端身份交换与网页授权完全同构，共用 {@link AbstractFeishuOAuth2Channel}。</p>
 *
 * @author lianghang
 */
public class FeishuQrAuthChannel extends AbstractFeishuOAuth2Channel {

    public static final String CHANNEL_CODE = "feishu_qr";

    public FeishuQrAuthChannel() {
        this(new FeishuDefaultHttpClient());
    }

    public FeishuQrAuthChannel(FeishuHttpClient http) {
        super(http);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
