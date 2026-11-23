package com.sfi.conflux.auth.autoconfigure;

import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.engine.AuthGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 conflux.auth.configs.<configId>.<channelCode> 配置被 starter 装载，
 * 且 AuthGateway 经 SPI 自动注册内置渠道、自定义配置源/state 缓存可覆盖。不触发真实网络。
 *
 * @author lianghang
 */
class AuthAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private AuthGateway gateway(AuthConfigProvider provider) {
        AuthAutoConfiguration auto = new AuthAutoConfiguration();
        ObjectProvider<AuthConfigProvider> configProvider = mock(ObjectProvider.class);
        ObjectProvider<com.sfi.conflux.auth.support.StateCache> stateCache = mock(ObjectProvider.class);
        when(configProvider.getIfAvailable()).thenReturn(provider);
        when(stateCache.getIfAvailable()).thenReturn(null);
        return auto.authGateway(configProvider, stateCache,
                java.util.Collections.<com.sfi.conflux.auth.channel.AuthChannel>emptyList());
    }

    @Test
    void shouldBindOurConfigStructureIntoProvider() {
        AuthProperties props = new AuthProperties();
        AuthProperties.ChannelProperties channel = new AuthProperties.ChannelProperties();
        channel.setClientId("wx-app-id");
        channel.setClientSecret("wx-secret");
        channel.setRedirectUri("https://example.com/callback");
        channel.setScope("snsapi_login");
        channel.getAttr().put("tokenVersion", "v3");
        Map<String, AuthProperties.ChannelProperties> channels =
                new LinkedHashMap<String, AuthProperties.ChannelProperties>();
        channels.put("wechat_open", channel);
        props.getConfigs().put("demo-boot", channels);

        AuthConfigProvider provider = new PropertiesAuthConfigProvider(props);
        AuthConfig cfg = provider.getConfig("demo-boot", "wechat_open");

        assertThat(cfg.getConfigId()).isEqualTo("demo-boot");
        assertThat(cfg.getChannelCode()).isEqualTo("wechat_open");
        assertThat(cfg.getClientId()).isEqualTo("wx-app-id");
        assertThat(cfg.getClientSecret()).isEqualTo("wx-secret");
        assertThat(cfg.getRedirectUri()).isEqualTo("https://example.com/callback");
        assertThat(cfg.getScope()).isEqualTo("snsapi_login");
        assertThat(cfg.getAttr()).containsEntry("tokenVersion", "v3");
    }

    @Test
    void shouldAutoRegisterBuiltinChannelsEvenWithoutConfig() {
        AuthProperties props = new AuthProperties();
        AuthGateway gw = gateway(new PropertiesAuthConfigProvider(props));

        // 内置渠道经 SPI 自动注册，与是否配置无关；24 形态全部在册
        assertThat(gw.registeredChannels()).contains(
                "wechat_open", "wechat_mp", "wechat_mini",
                "dingtalk_qr", "dingtalk_mini", "feishu_web", "alipay_web");
        assertThat(gw.registeredChannels()).hasSize(24);
    }

    @Test
    void missingConfigShouldThrowConfigMissing() {
        AuthConfigProvider provider = new PropertiesAuthConfigProvider(new AuthProperties());
        assertThatThrownBy(() -> provider.getConfig("none", "wechat_open"))
                .isInstanceOf(com.sfi.conflux.common.exception.GatewayException.class)
                .hasMessageContaining("登录渠道配置缺失");
    }

    @Test
    void allConfigsShouldFlattenAllConfiguredChannels() {
        AuthProperties props = new AuthProperties();
        Map<String, AuthProperties.ChannelProperties> channels =
                new LinkedHashMap<String, AuthProperties.ChannelProperties>();
        channels.put("wechat_open", new AuthProperties.ChannelProperties());
        channels.put("alipay_web", new AuthProperties.ChannelProperties());
        props.getConfigs().put("app-a", channels);

        assertThat(new PropertiesAuthConfigProvider(props).allConfigs()).hasSize(2);
        assertThat(new PropertiesAuthConfigProvider(props).allConfigs())
                .containsKeys("app-a#wechat_open", "app-a#alipay_web");
    }
}
