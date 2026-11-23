package com.sfi.conflux.auth.channels.alipay;

/**
 * 支付宝手机网站 H5 登录（端外）：channelCode = alipay_h5。
 *
 * <p>浏览器端拼接标准授权链接，可用短链 alipays://platformapi/startapp?appId=20000067&url=...
 * 唤起支付宝客户端授权（前端拼接，门面只产出授权 URL）。
 * 后端交换与电脑网站同构。</p>
 *
 * @author lianghang
 */
public class AliH5AuthChannel extends AbstractAliOAuth2Channel {

    public static final String CHANNEL_CODE = "alipay_h5";

    public AliH5AuthChannel() {
        this(new AliClientHolder());
    }

    public AliH5AuthChannel(AliClientHolder holder) {
        super(holder);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
