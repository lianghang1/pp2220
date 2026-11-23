package com.sfi.conflux.pay.autoconfigure;

import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.config.ChannelConfigProvider;
import com.sfi.conflux.pay.engine.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter 装配测试：验证自动配置、默认配置提供者与网关 Bean 的完整构建。
 * SDK 为无状态渠道聚合层，无需使用方注册任何存储 Bean。
 *
 * @author lianghang
 */
@SpringBootTest(classes = PayGatewayAutoConfigurationTest.TestApp.class, properties = {
        "conflux.pay.configs.demo.mock.notifyUrl=http://127.0.0.1/callback/mock",
        "conflux.pay.configs.demo.mock.sandbox=true",
        "conflux.pay.configs.demo.mock.attr.appKey=demo-app-key",
        "conflux.pay.configs.demo.mock.attr.payOutcome=paying"
})
class PayGatewayAutoConfigurationTest {
    @Autowired
    private PaymentGateway gateway;
    @Autowired
    private ChannelConfigProvider configProvider;

    @Test
    void gatewayShouldBeAssembledWithMockChannel() {
        assertThat(gateway.registeredChannels()).contains("mock");
    }

    @Test
    void propertiesConfigProviderShouldResolveConfig() {
        ChannelConfig config = configProvider.getConfig("demo", "mock");
        assertThat(config.getConfigId()).isEqualTo("demo");
        assertThat(config.getNotifyUrl()).isEqualTo("http://127.0.0.1/callback/mock");
        assertThat(config.isSandbox()).isTrue();
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
