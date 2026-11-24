package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.support.InMemoryNotificationConfigProvider;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证通知域 NotificationConfigProvider 契约与支付/发票对齐：send 每次从 provider 读取配置，
 * 缺失配置 fail-fast。用一个「未知渠道」让装载阶段即失败（CHANNEL_NOT_FOUND），
 * 从而只验证读取/装载链路、不触发任何真实短信网络请求。
 *
 * @author lianghang
 */
class NotificationConfigProviderContractTest {

    private static NotificationSendRequest req(String channelCode) {
        return new NotificationSendRequest().setChannelCode(channelCode).setPhone("18888888888").setContent("hi");
    }

    @Test
    void customProviderShouldBeReadOnEverySend() {
        final int[] calls = {0};
        NotificationConfigProvider provider = new NotificationConfigProvider() {
            @Override
            public NotificationConfig getConfig(String configId, String channelCode) {
                calls[0]++;
                // 未知渠道：ensureLoaded 会抛 CHANNEL_NOT_FOUND，但 getConfig 已被调用
                return new NotificationConfig().setConfigId(configId).setChannelCode(channelCode);
            }
        };
        NotificationGateway gateway = NotificationGateway.builder().configProvider(provider).build();

        assertThatThrownBy(() -> gateway.send("dbapp", req("no-such-channel")))
                .isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.send("dbapp", req("no-such-channel")))
                .isInstanceOf(GatewayException.class);

        assertThat(calls[0]).isEqualTo(2); // 每次 send 都读 provider（对齐支付/发票）
    }

    @Test
    void missingConfigFromProviderShouldFailFast() {
        NotificationConfigProvider provider = new InMemoryNotificationConfigProvider(); // 空 provider
        NotificationGateway gateway = NotificationGateway.builder().configProvider(provider).build();
        assertThatThrownBy(() -> gateway.send("nope", req("alibaba")))
                .isInstanceOf(GatewayException.class)
                .satisfies(e -> assertThat(((GatewayException) e).getErrorCode().getCode()).isEqualTo(4103));
    }
}
