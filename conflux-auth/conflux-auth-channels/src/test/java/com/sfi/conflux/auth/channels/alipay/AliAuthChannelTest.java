package com.sfi.conflux.auth.channels.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.channel.AuthChannel;
import com.sfi.conflux.auth.channels.BuiltinAuthChannelRegistrarImpl;
import com.sfi.conflux.auth.config.AuthConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付宝全家桶 6 形态测试：授权入口、token/userinfo 交换、open_id 兜底、
 * 公钥/证书 execute 分支、小程序独立链路、SPI 注册。
 *
 * @author lianghang
 */
class AliAuthChannelTest {

    private AuthConfig baseConfig() {
        return new AuthConfig()
                .setClientId("app-001")
                .setClientSecret("private-key")
                .setRedirectUri("https://example.com/callback");
    }

    /** 测试专用：网页系默认开 state 校验，exchange 单测未走 authorize，故关闭以聚焦交换逻辑 */
    private AliWebAuthChannel noStateChannel(AliClientHolder holder) {
        return new AliWebAuthChannel(holder) {
            @Override
            public boolean requireStateCheck() {
                return false;
            }
        };
    }

    private AlipaySystemOauthTokenResponse tokenResp(String openId, String userId) {
        AlipaySystemOauthTokenResponse r = new AlipaySystemOauthTokenResponse();
        r.setCode("10000");
        r.setAccessToken("access-001");
        r.setExpiresIn("7200");
        r.setOpenId(openId);
        r.setUserId(userId);
        return r;
    }

    private AlipayUserInfoShareResponse infoResp(String openId, String userId) {
        AlipayUserInfoShareResponse r = new AlipayUserInfoShareResponse();
        r.setCode("10000");
        r.setOpenId(openId);
        r.setUserId(userId);
        r.setNickName("小明");
        r.setUserName("张三");
        r.setAvatar("https://avatar");
        r.setGender("M");
        r.setProvince("浙江");
        r.setCity("杭州");
        return r;
    }

    @Test
    void alipay_web_授权URL含appid_scope_redirect_state() {
        AliWebAuthChannel channel = new AliWebAuthChannel(new AliClientHolder());
        // authorize 是 final，会走基类生成 state 并写缓存——需注入 StateCache
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        AuthorizeResult result = channel.authorize(baseConfig(), null);
        String url = result.getAuthorizeUrl();
        assertThat(url).startsWith("https://openauth.alipay.com/oauth2/publicAppAuthorize.htm");
        assertThat(url).contains("app_id=app-001");
        assertThat(url).contains("scope=auth_user");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("state=");
    }

    @Test
    void alipay_web_code换token再换userinfo_openId优先() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AliClientHolder holder = mock(AliClientHolder.class);
        when(holder.client(any(AuthConfig.class))).thenReturn(client);

        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(tokenResp("open-new", "2088-old"));
        when(client.execute(any(AlipayUserInfoShareRequest.class), anyString()))
                .thenReturn(infoResp("info-open", "info-user"));

        AliWebAuthChannel channel = noStateChannel(holder);
        Map<String, String> params = new HashMap<String, String>();
        params.put("auth_code", "the-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        AuthUser user = result.getData();
        assertThat(user.getChannelCode()).isEqualTo("alipay_web");
        // info 的 open_id 优先
        assertThat(user.getOpenId()).isEqualTo("info-open");
        assertThat(user.getUsername()).isEqualTo("张三");
        assertThat(user.getAvatar()).isEqualTo("https://avatar");
        assertThat(user.getGender()).isEqualTo("M");
    }

    @Test
    void alipay_web_仅user_id时兜底_兼容老商户() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AliClientHolder holder = mock(AliClientHolder.class);
        when(holder.client(any(AuthConfig.class))).thenReturn(client);
        AlipaySystemOauthTokenResponse token = new AlipaySystemOauthTokenResponse();
        token.setCode("10000");
        token.setAccessToken("at");
        token.setUserId("2088-only"); // 无 open_id
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(token);
        AlipayUserInfoShareResponse info = new AlipayUserInfoShareResponse();
        info.setCode("10000");
        info.setUserId("2088-only");
        when(client.execute(any(AlipayUserInfoShareRequest.class), anyString())).thenReturn(info);

        AliWebAuthChannel channel = noStateChannel(holder);
        Map<String, String> params = new HashMap<String, String>();
        params.put("auth_code", "c");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOpenId()).isEqualTo("2088-only");
    }

    @Test
    void alipay_web_证书模式走certificateExecute() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AliClientHolder holder = mock(AliClientHolder.class);
        when(holder.client(any(AuthConfig.class))).thenReturn(client);
        when(client.certificateExecute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(tokenResp("open", null));
        when(client.certificateExecute(any(AlipayUserInfoShareRequest.class), anyString()))
                .thenReturn(infoResp("open", null));

        AuthConfig config = baseConfig();
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("signMode", "cert");
        config.setAttr(attr);

        AliWebAuthChannel channel = noStateChannel(holder);
        Map<String, String> params = new HashMap<String, String>();
        params.put("auth_code", "c");
        AuthResult<AuthUser> result = channel.exchange(config, new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        verify(client).certificateExecute(any(AlipaySystemOauthTokenRequest.class));
        verify(client).certificateExecute(any(AlipayUserInfoShareRequest.class), anyString());
        verify(client, never()).execute(any(AlipaySystemOauthTokenRequest.class));
    }

    @Test
    void alipay_h5_jsapi_关闭state且返回JSAPI参数() {
        AliH5JsapiAuthChannel channel = new AliH5JsapiAuthChannel(new AliClientHolder());
        assertThat(channel.requireStateCheck()).isFalse();
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        AuthorizeResult result = channel.authorize(baseConfig(), null);
        assertThat(result.getJsApiParams()).isNotNull();
        assertThat(result.getJsApiParams().get("action")).isEqualTo("ap.getAuthCode");
        assertThat(result.getAuthorizeUrl()).isNull();
    }

    @Test
    void alipay_app_返回SDK参数且关闭state() {
        AliAppAuthChannel channel = new AliAppAuthChannel(new AliClientHolder());
        assertThat(channel.requireStateCheck()).isFalse();
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        AuthorizeResult result = channel.authorize(baseConfig(), null);
        assertThat(result.getJsApiParams().get("action")).isEqualTo("AlipayLoginSDK");
    }

    @Test
    void alipay_mini_只换token不调userinfo() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AliClientHolder holder = mock(AliClientHolder.class);
        when(holder.client(any(AuthConfig.class))).thenReturn(client);
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(tokenResp("mini-open", null));

        AliMiniAuthChannel channel = new AliMiniAuthChannel(holder);
        assertThat(channel.requireStateCheck()).isFalse();
        Map<String, String> params = new HashMap<String, String>();
        params.put("authCode", "mini-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOpenId()).isEqualTo("mini-open");
        // 关键：小程序绝不调用 alipay.user.info.share
        verify(client, never()).execute(any(AlipayUserInfoShareRequest.class));
        verify(client, never()).execute(any(AlipayUserInfoShareRequest.class), anyString());
    }

    @Test
    void alipay_mini_仅user_id时兜底() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AliClientHolder holder = mock(AliClientHolder.class);
        when(holder.client(any(AuthConfig.class))).thenReturn(client);
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(tokenResp(null, "2088-mini"));

        AliMiniAuthChannel channel = new AliMiniAuthChannel(holder);
        Map<String, String> params = new HashMap<String, String>();
        params.put("authCode", "c");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOpenId()).isEqualTo("2088-mini");
    }

    @Test
    void spi_自动注册支付宝全家桶6种形态() {
        List<AuthChannel> channels = new BuiltinAuthChannelRegistrarImpl().builtinChannels();
        assertThat(channels).extracting(AuthChannel::channelCode).contains(
                "alipay_web", "alipay_h5", "alipay_h5_jsapi", "alipay_h5_inapp", "alipay_app", "alipay_mini");
    }

    @Test
    void alipay_web_缺code返回失败() {
        AliWebAuthChannel channel = noStateChannel(new AliClientHolder());
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(new HashMap<String, String>()));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MISSING_CODE");
    }
}
