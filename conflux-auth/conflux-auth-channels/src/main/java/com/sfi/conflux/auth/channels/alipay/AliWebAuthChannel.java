package com.sfi.conflux.auth.channels.alipay;

/**
 * 支付宝电脑网站登录：channelCode = alipay_web。
 *
 * <p>拼接授权链接 openauth.alipay.com/oauth2/publicAppAuthorize.htm，浏览器打开，
 * 回调 auth_code → alipay.system.oauth.token → alipay.user.info.share（签名/验签走官方 SDK）。</p>
 *
 * @author lianghang
 */
public class AliWebAuthChannel extends AbstractAliOAuth2Channel {

    public static final String CHANNEL_CODE = "alipay_web";

    public AliWebAuthChannel() {
        this(new AliClientHolder());
    }

    public AliWebAuthChannel(AliClientHolder holder) {
        super(holder);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }
}
