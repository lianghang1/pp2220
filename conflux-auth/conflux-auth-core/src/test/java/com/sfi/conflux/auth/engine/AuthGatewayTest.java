package com.sfi.conflux.auth.engine;

import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.support.InMemoryStateCache;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录门面契约测试：验证三条核心约定——
 * ① 门面纯路由（只查配置+找渠道+转发）；
 * ② state 在渠道基类生成、校验、取出即删（防重放）；
 * ③ 参数名不写死（provider 按自己的名字取票据）。
 *
 * @author lianghang
 */
class AuthGatewayTest {

    /** 测试渠道：票据参数名故意用非标准的 auth_code，验证不写死 code */
    private static class TestChannel extends AbstractAuthChannel {
        private String seenState;
        private int exchangeCount;

        @Override
        public String channelCode() {
            return "test_web";
        }

        @Override
        protected AuthorizeResult doBuildAuthorize(AuthConfig config, String state, AuthorizeOption option) {
            seenState = state;
            return new AuthorizeResult().setAuthorizeUrl("https://auth.example.com/?state=" + state);
        }

        @Override
        protected AuthResult<AuthUser> doExchange(AuthConfig config, AuthCallbackRequest request) {
            exchangeCount++;
            String ticket = request.firstParam("code", "auth_code");
            return AuthResult.ok(new AuthUser().setChannelCode(channelCode()).setOpenId(ticket));
        }

        @Override
        public String resolveState(AuthCallbackRequest request) {
            return request.param("st"); // 非标准 state 参数名
        }
    }

    private AuthConfigProvider singleConfig() {
        return new AuthConfigProvider() {
            @Override
            public AuthConfig getConfig(String configId, String channelCode) {
                return new AuthConfig().setClientId("cid").setClientSecret("sec");
            }
        };
    }

    @Test
    void authorize_生成state并写入缓存() {
        TestChannel channel = new TestChannel();
        InMemoryStateCache cache = new InMemoryStateCache();
        AuthGateway gw = AuthGateway.builder()
                .configProvider(singleConfig())
                .stateCache(cache)
                .channel(channel)
                .build();

        AuthorizeResult result = gw.authorize("cfg1", "test_web");

        assertThat(result.getAuthorizeUrl()).contains("state=");
        assertThat(channel.seenState).isNotBlank();
        assertThat(cache.size()).isEqualTo(1); // state 已写入缓存
    }

    @Test
    void parseCallback_state取出即删_防重放() {
        TestChannel channel = new TestChannel();
        InMemoryStateCache cache = new InMemoryStateCache();
        AuthGateway gw = AuthGateway.builder()
                .configProvider(singleConfig())
                .stateCache(cache)
                .channel(channel)
                .build();

        gw.authorize("cfg1", "test_web");
        String state = channel.seenState;

        Map<String, String> params = new HashMap<String, String>();
        params.put("st", state);       // 非标准参数名
        params.put("auth_code", "TICKET-123"); // 非标准票据名
        AuthCallbackRequest req = new AuthCallbackRequest(params);

        AuthResult<AuthUser> first = gw.parseCallback("cfg1", "test_web", req);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getData().getOpenId()).isEqualTo("TICKET-123"); // 按别名取到票据
        assertThat(cache.size()).isEqualTo(0); // 取出即删

        AuthResult<AuthUser> replay = gw.parseCallback("cfg1", "test_web", req);
        assertThat(replay.isSuccess()).isFalse(); // 重放被拒
        assertThat(replay.getErrorCode()).isEqualTo("ILLEGAL_STATE");
    }

    @Test
    void 门面只注册渠道_无内置渠道也能工作() {
        TestChannel channel = new TestChannel();
        AuthGateway gw = AuthGateway.builder()
                .configProvider(singleConfig())
                .channel(channel)
                .build();

        assertThat(gw.registeredChannels()).containsExactly("test_web");
    }
}
