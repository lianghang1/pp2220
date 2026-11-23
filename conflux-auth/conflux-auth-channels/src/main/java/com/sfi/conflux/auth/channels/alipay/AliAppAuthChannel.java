package com.sfi.conflux.auth.channels.alipay;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝 APP 登录：channelCode = alipay_app。
 *
 * <p>APP 集成支付宝登录 SDK（完整版 / 极简版）拿 auth_code，前端传给后端 —— 不回传 state，
 * 故关闭 state 校验，授权入口返回前端需调用的 SDK 参数。后端交换链路与网页系完全一致。</p>
 *
 * @author lianghang
 */
public class AliAppAuthChannel extends AbstractAliOAuth2Channel {

    public static final String CHANNEL_CODE = "alipay_app";

    public AliAppAuthChannel() {
        this(new AliClientHolder());
    }

    public AliAppAuthChannel(AliClientHolder holder) {
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
        jsApi.put("action", "AlipayLoginSDK");
        jsApi.put("clientId", config.getClientId());
        return new AuthorizeResult().setJsApiParams(jsApi);
    }
}
