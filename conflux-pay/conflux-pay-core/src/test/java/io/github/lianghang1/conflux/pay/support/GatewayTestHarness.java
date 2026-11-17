package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.pay.engine.PaymentGateway;

/**
 * 测试装配台：组装仅含模拟渠道与配置的网关实例（SDK 无状态，无存储依赖）。
 *
 * @author lianghang
 */
public final class GatewayTestHarness {

    public static final String APP_ID = "app-001";

    public final TestChannelProvider channelProvider = new TestChannelProvider();
    public final PaymentGateway gateway;

    static {
        try {
            io.github.lianghang1.conflux.pay.channel.PayMethod.register(TestChannelProvider.METHOD_PAY, TestChannelProvider.CODE);
            io.github.lianghang1.conflux.pay.channel.PayMethod.register(TestChannelProvider.METHOD_TRANS, TestChannelProvider.CODE);
        } catch (IllegalStateException ignored) {
            // 同 JVM 内重复构建时忽略
        }
    }

    public GatewayTestHarness() {
        this.gateway = PaymentGateway.builder()
                .channel(channelProvider)
                .configProvider(new TestChannelProvider.TestConfigProvider(APP_ID))
                .build();
    }
}
