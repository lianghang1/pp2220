package com.sfi.conflux.auth.channels.alipay;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝手机网站 H5 登录（端内 JSAPI 半屏授权）：channelCode = alipay_h5_jsapi。
 *
 * <p>支付宝客户端内网页调 JSAPI {@code ap.getAuthCode} 弹半屏授权浮层，前端拿到 auth_code
 * 传给后端 —— 不经浏览器跳转回调，平台不回传 state，故关闭 state 校验，授权入口返回前端
 * 需调用的 JSAPI 参数（action/clientId）。后端拿到 code 后交换链路与网页系完全一致。</p>
 *
 * @author lianghang
 */
public class AliH5JsapiAuthChannel extends AbstractAliOAuth2Channel {

    public static final String CHANNEL_CODE = "alipay_h5_jsapi";

    public AliH5JsapiAuthChannel() {
        this(new AliClientHolder());
    }

    public AliH5JsapiAuthChannel(AliClientHolder holder) {
        super(holder);
    }

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public boolean requireStateCheck() {
        return false;
    }

    @Override
    protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
        Map<String, Object> jsApi = new LinkedHashMap<String, Object>();
        jsApi.put("action", "ap.getAuthCode");
        jsApi.put("clientId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }
}
