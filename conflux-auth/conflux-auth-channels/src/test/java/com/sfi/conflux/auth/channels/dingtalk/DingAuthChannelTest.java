package com.sfi.conflux.auth.channels.dingtalk;

import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.engine.AuthGateway;
import com.sfi.conflux.auth.support.InMemoryStateCache;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉钉全家桶 provider 契约测试：注入 mock DingHttpClient，不依赖真实网络。
 */
class DingAuthChannelTest {

    @Test
    void dingtalk_qr_授权URL是oauth2auth端点() {
        DingQrAuthChannel channel = new DingQrAuthChannel(new DingDefaultHttpClient());
        channel.setStateCache(new InMemoryStateCache());
        AuthorizeResult result = channel.authorize(baseConfig(), null);
        String url = result.getAuthorizeUrl();

        assertThat(url).contains("login.dingtalk.com/oauth2/auth");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=ding123");
        assertThat(url).contains("scope=openid");
        assertThat(url).contains("prompt=consent");
        assertThat(url).contains("state=");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fa.com%2Fcb");
    }

    @Test
    void dingtalk_web_默认scope含corpid() {
        DingWebAuthChannel channel = new DingWebAuthChannel(new DingDefaultHttpClient());
        channel.setStateCache(new InMemoryStateCache());
        AuthorizeResult result = channel.authorize(baseConfig(), null);
        assertThat(result.getAuthorizeUrl()).contains("scope=openid+corpid");
    }

    @Test
    void dingtalk_qr_code换token换userinfo并归一AuthUser() {
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.postJson(contains("/v1.0/oauth2/userAccessToken"), nullable(Map.class), anyString()))
                .thenReturn("{\"accessToken\":\"at-001\",\"refreshToken\":\"rt\",\"expireIn\":7200}");
        when(http.get(contains("/v1.0/contact/users/me"), nullable(Map.class)))
                .thenReturn("{\"unionId\":\"union-001\",\"openId\":\"open-001\",\"nick\":\"李四\","
                        + "\"avatarUrl\":\"https://avatar/l.png\",\"email\":\"l@a.com\",\"gender\":\"M\"}");

        DingQrAuthChannel channel = new DingQrAuthChannel(http) {
            @Override
            public boolean requireStateCheck() { return false; }
        };
        channel.setStateCache(new InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("authCode", "the-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        AuthUser user = result.getData();
        assertThat(user.getChannelCode()).isEqualTo("dingtalk_qr");
        assertThat(user.getOpenId()).isEqualTo("open-001");
        assertThat(user.getUnionId()).isEqualTo("union-001");
        assertThat(user.getUsername()).isEqualTo("李四");
        assertThat(user.getAvatar()).isEqualTo("https://avatar/l.png");
        assertThat(user.getEmail()).isEqualTo("l@a.com");
        assertThat(user.getGender()).isEqualTo("M");
        assertThat(user.getRawUserInfo()).containsKey("nick");
    }

    @Test
    void dingtalk_h5_internal_免登走gettoken加getuserinfo且JSAPI入口() {
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.get(contains("/gettoken"), nullable(Map.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"app-token\"}");
        when(http.postJson(contains("/topapi/v2/user/getuserinfo"), nullable(Map.class), anyString()))
                .thenReturn("{\"errcode\":0,\"result\":{\"userid\":\"user-001\",\"name\":\"王五\"}}");

        DingH5InternalAuthChannel channel = new DingH5InternalAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        AuthorizeResult authorize = channel.authorize(baseConfig(), null);
        assertThat(authorize.getJsApiParams()).containsEntry("action", "dd.getAuthCode");

        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "free-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getChannelCode()).isEqualTo("dingtalk_h5_internal");
        assertThat(result.getData().getOpenId()).isEqualTo("user-001");
        assertThat(result.getData().getUsername()).isEqualTo("王五");
    }

    @Test
    void dingtalk_h5_isv_免登归一openId() {
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.get(contains("/gettoken"), nullable(Map.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"isv-token\"}");
        when(http.postJson(contains("/topapi/v2/user/getuserinfo"), nullable(Map.class), anyString()))
                .thenReturn("{\"errcode\":0,\"result\":{\"userId\":\"isv-user-001\"}}");

        DingH5IsvAuthChannel channel = new DingH5IsvAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        AuthorizeResult authorize = channel.authorize(baseConfig(), null);
        assertThat(authorize.getJsApiParams()).containsEntry("action", "dd.requestAuthCode");

        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "isv-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOpenId()).isEqualTo("isv-user-001");
    }

    @Test
    void dingtalk_mini_签名换userinfo归一openid与unionid() {
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.postJson(contains("/sns/getuserinfo_bycode"), nullable(Map.class), anyString()))
                .thenReturn("{\"errcode\":0,\"user_info\":{\"openid\":\"mini-open\",\"unionid\":\"mini-union\",\"nick\":\"小赵\"}}");

        DingMiniAuthChannel channel = new DingMiniAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        assertThat(channel.authorize(baseConfig(), null).getJsApiParams())
                .containsEntry("action", "dd.getAuthCode");

        Map<String, String> params = new HashMap<String, String>();
        params.put("authCode", "mini-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOpenId()).isEqualTo("mini-open");
        assertThat(result.getData().getUnionId()).isEqualTo("mini-union");
        assertThat(result.getData().getUsername()).isEqualTo("小赵");
    }

    @Test
    void dingtalk_mini_请求URL含accessKey与timestamp与signature且signature已urlEncode() {
        org.mockito.ArgumentCaptor<String> urlCap = org.mockito.ArgumentCaptor.forClass(String.class);
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.postJson(contains("/sns/getuserinfo_bycode"), nullable(Map.class), anyString()))
                .thenReturn("{\"errcode\":0,\"user_info\":{\"openid\":\"o\",\"unionid\":\"u\",\"nick\":\"n\"}}");

        DingMiniAuthChannel channel = new DingMiniAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("authCode", "c");
        channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        org.mockito.Mockito.verify(http).postJson(urlCap.capture(), nullable(Map.class), anyString());
        String url = urlCap.getValue();
        assertThat(url).contains("accessKey=ding123");
        assertThat(url).contains("&timestamp=");
        // signature 值中的 Base64 特殊字符应被 urlEncode（+ -> %2B/%20, = -> %3D, / -> %2F），
        // 断言 signature 段内不残留裸 = / +
        String sigValue = url.substring(url.indexOf("&signature=") + "&signature=".length());
        assertThat(sigValue).doesNotContain("=").doesNotContain("/").doesNotContain("+");
    }

    @Test
    void dingtalk_mini_签名向量与官方一致() throws Exception {
        // 官方规范: HmacSHA256(密钥=appSecret, 数据=timestamp) -> 标准 Base64
        // 该期望值由 Python hmac 独立计算: base64(hmac.new(b'secret', b'1234567890', sha256))
        String sig = DingMiniAuthChannel.dingSignature("secret", "1234567890");
        assertThat(sig).isEqualTo("Bgs+chJF8gBA3xW2542Tm7B7l571zTPfLMBiCBwOp2c=");
    }

    @Test
    void dingtalk_admin_免登走ssoGettoken() {
        DingHttpClient http = mock(DingHttpClient.class);
        when(http.get(contains("/sso/gettoken"), nullable(Map.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"sso-token\"}");
        when(http.postJson(contains("/topapi/v2/user/getuserinfo"), nullable(Map.class), anyString()))
                .thenReturn("{\"errcode\":0,\"result\":{\"userid\":\"admin-001\"}}");

        DingAdminAuthChannel channel = new DingAdminAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "admin-code");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getChannelCode()).isEqualTo("dingtalk_admin");
        assertThat(result.getData().getOpenId()).isEqualTo("admin-001");
    }

    @Test
    void spi_自动注册钉钉全家桶6种形态() {
        AuthConfigProvider provider = mock(AuthConfigProvider.class);
        AuthGateway gateway = AuthGateway.builder().configProvider(provider).build();
        assertThat(gateway.registeredChannels()).contains(
                "dingtalk_qr", "dingtalk_web", "dingtalk_h5_internal",
                "dingtalk_h5_isv", "dingtalk_mini", "dingtalk_admin");
    }

    private static AuthConfig baseConfig() {
        return new AuthConfig()
                .setClientId("ding123")
                .setClientSecret("secret")
                .setRedirectUri("https://a.com/cb");
    }
}
