package com.sfi.conflux.auth.channels.feishu;

import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.engine.AuthGateway;
import com.sfi.conflux.auth.support.InMemoryStateCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 飞书全家桶 provider 契约测试：注入 mock FeishuHttpClient，不依赖真实网络。
 */
class FeishuAuthChannelTest {

    /** 测试专用：关闭 state 校验，便于直接测 exchange 的身份交换逻辑 */
    private static class TestWebChannel extends FeishuWebAuthChannel {
        TestWebChannel(FeishuHttpClient http) {
            super(http);
        }

        @Override
        public boolean requireStateCheck() {
            return false;
        }
    }

    @Test
    void feishu_web_授权URL是authorize端点且参数正确() {
        TestWebChannel channel = new TestWebChannel(new FeishuDefaultHttpClient());
        channel.setStateCache(new InMemoryStateCache());
        AuthorizeResult result = channel.authorize(baseConfig(), null);
        String url = result.getAuthorizeUrl();

        assertThat(url).contains("accounts.feishu.cn/open-apis/authen/v1/authorize");
        assertThat(url).contains("app_id=cli123");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fa.com%2Fcb");
    }

    @Test
    void feishu_web_defaultToken端点走v3() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        stubOAuth(http);
        channel(http).exchange(baseConfig(), cb("code", "the-code"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(urlCap.capture(), nullable(Map.class), anyString());
        assertThat(urlCap.getValue()).isEqualTo(FeishuSupport.TOKEN_URL_V3);
    }

    @Test
    void feishu_web_attr指定v2时token端点切到v2() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        stubOAuth(http);
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("tokenVersion", "v2");
        AuthConfig config = baseConfig().setAttr(attr);
        channel(http).exchange(config, cb("code", "the-code"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(urlCap.capture(), nullable(Map.class), anyString());
        assertThat(urlCap.getValue()).isEqualTo(FeishuSupport.TOKEN_URL_V2);
    }

    @Test
    void feishu_web_attr指定完整tokenUrl时优先覆盖() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        stubOAuth(http);
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("tokenUrl", "https://custom.example.com/token");
        AuthConfig config = baseConfig().setAttr(attr);
        channel(http).exchange(config, cb("code", "the-code"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(urlCap.capture(), nullable(Map.class), anyString());
        assertThat(urlCap.getValue()).isEqualTo("https://custom.example.com/token");
    }

    @Test
    void feishu_web_code换token换userinfo并归一AuthUser() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        when(http.postJson(eq(FeishuSupport.TOKEN_URL_V3), nullable(Map.class), anyString()))
                .thenReturn("{\"code\":0,\"access_token\":\"u-token\",\"token_type\":\"Bearer\",\"expires_in\":7200}");
        when(http.get(eq(FeishuSupport.USERINFO_URL), nullable(Map.class)))
                .thenReturn("{\"code\":0,\"data\":{\"open_id\":\"ou-001\",\"union_id\":\"on-001\","
                        + "\"user_id\":\"uid-001\",\"name\":\"张三\",\"en_name\":\"Tom\","
                        + "\"avatar_url\":\"https://avatar/a.png\",\"email\":\"z@feishu.cn\"}}");

        AuthResult<AuthUser> result = channel(http).exchange(baseConfig(), cb("code", "the-code"));

        assertThat(result.isSuccess()).isTrue();
        AuthUser user = result.getData();
        assertThat(user.getChannelCode()).isEqualTo("feishu_web");
        assertThat(user.getOpenId()).isEqualTo("ou-001");
        assertThat(user.getUnionId()).isEqualTo("on-001");
        assertThat(user.getUsername()).isEqualTo("张三");
        assertThat(user.getAvatar()).isEqualTo("https://avatar/a.png");
        assertThat(user.getEmail()).isEqualTo("z@feishu.cn");
        assertThat(user.getRawUserInfo()).containsKey("user_id");
    }

    @Test
    void feishu_web_token错误码被识别为失败() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        when(http.postJson(eq(FeishuSupport.TOKEN_URL_V3), nullable(Map.class), anyString()))
                .thenReturn("{\"code\":20003,\"error\":\"invalid_grant\",\"error_description\":\"code 无效\"}");

        AuthResult<AuthUser> result = channel(http).exchange(baseConfig(), cb("code", "bad-code"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("FEISHU_TOKEN_ERROR");
    }

    @Test
    void feishu_mini_走appToken加code2session独立链路并归一() {
        FeishuHttpClient http = mock(FeishuHttpClient.class);
        when(http.postJson(eq(FeishuSupport.APP_TOKEN_URL), nullable(Map.class), anyString()))
                .thenReturn("{\"code\":0,\"app_access_token\":\"a-token\"}");
        when(http.postJson(eq(FeishuSupport.MINI_TOKEN_URL), nullable(Map.class), anyString()))
                .thenReturn("{\"code\":0,\"data\":{\"open_id\":\"ou-mini\",\"employee_id\":\"emp-001\","
                        + "\"session_key\":\"sk\",\"union_id\":\"DEPRECATED-DO-NOT-USE\"}}");

        FeishuMiniAuthChannel channel = new FeishuMiniAuthChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        assertThat(channel.authorize(baseConfig(), null).getJsApiParams())
                .containsEntry("action", "tt.requestAccess");

        AuthResult<AuthUser> result = channel.exchange(baseConfig(), cb("code", "mini-code"));
        assertThat(result.isSuccess()).isTrue();
        AuthUser user = result.getData();
        assertThat(user.getChannelCode()).isEqualTo("feishu_mini");
        assertThat(user.getOpenId()).isEqualTo("ou-mini");
        assertThat(user.getUsername()).isEqualTo("emp-001");
        // 官方明确 union_id 已废弃，不得映射到统一 unionId
        assertThat(user.getUnionId()).isNull();
    }

    @Test
    void feishu_mini_免登不走state校验() {
        FeishuMiniAuthChannel channel = new FeishuMiniAuthChannel(mock(FeishuHttpClient.class));
        assertThat(channel.requireStateCheck()).isFalse();
    }

    @Test
    void spi_自动注册飞书全家桶5种形态() {
        AuthConfigProvider provider = mock(AuthConfigProvider.class);
        AuthGateway gateway = AuthGateway.builder().configProvider(provider).build();
        assertThat(gateway.registeredChannels()).contains(
                "feishu_web", "feishu_qr", "feishu_app", "feishu_inapp", "feishu_mini");
    }

    // ---- helpers ----

    private static TestWebChannel channel(FeishuHttpClient http) {
        TestWebChannel channel = new TestWebChannel(http);
        channel.setStateCache(new InMemoryStateCache());
        return channel;
    }

    private static void stubOAuth(FeishuHttpClient http) {
        when(http.postJson(anyString(), nullable(Map.class), anyString()))
                .thenReturn("{\"code\":0,\"access_token\":\"u-token\"}");
        when(http.get(anyString(), nullable(Map.class)))
                .thenReturn("{\"code\":0,\"data\":{\"open_id\":\"ou\",\"union_id\":\"un\",\"name\":\"n\"}}");
    }

    private static AuthCallbackRequest cb(String key, String value) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(key, value);
        return new AuthCallbackRequest(params);
    }

    private static AuthConfig baseConfig() {
        return new AuthConfig()
                .setClientId("cli123")
                .setClientSecret("secret")
                .setRedirectUri("https://a.com/cb");
    }
}
