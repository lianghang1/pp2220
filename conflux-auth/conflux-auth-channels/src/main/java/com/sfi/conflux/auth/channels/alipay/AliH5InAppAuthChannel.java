package com.sfi.conflux.auth.channels.alipay;

/**
 * 支付宝手机网站 H5 登录（端内链接）：channelCode = alipay_h5_inapp。
 *
 * <p>支付宝客户端内打开网页，使用标准授权链接，端内免二次跳转确认。
 * 后端交换与端外同构，区别仅在前端打开环境。</p>
 *
 * @author lianghang
 */
public class AliH5InAppAuthChannel extends AbstractAliOAuth2Channel {

    public static final String CHANNEL_CODE = "alipay_h5_inapp";

    public AliH5InAppAuthChannel() {
        this(new AliClientHolder());
    }

    public AliH5InAppAuthChannel(AliClientHolder holder) {
        super(holder);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
