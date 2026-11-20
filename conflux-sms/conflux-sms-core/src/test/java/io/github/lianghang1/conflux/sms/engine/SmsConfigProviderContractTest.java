package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.support.InMemorySmsConfigProvider;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证短信域 SmsConfigProvider 契约与支付/发票对齐：send 每次从 provider 读取配置，
 * 缺失配置 fail-fast。用一个「未知渠道」让装载阶段即失败（CHANNEL_NOT_FOUND），
 * 从而只验证读取/装载链路、不触发任何真实短信网络请求。
 *
 * @author lianghang
 */
class SmsConfigProviderContractTest {

    private static SmsSendRequest req() {
        return new SmsSendRequest().setPhone("18888888888").setContent("hi");
    }

    @Test
    void customProviderShouldBeReadOnEverySend() {
        final int[] calls = {0};
        SmsConfigProvider provider = new SmsConfigProvider() {
            @Override
            public SmsChannelConfig getConfig(String configId, String channelCode) {
                calls[0]++;
                // 未知渠道：ensureLoaded 会抛 CHANNEL_NOT_FOUND，但 getConfig 已被调用
                return new SmsChannelConfig().setConfigId(configId).setChannelCode(channelCode);
            }
        };
        SmsGateway gateway = SmsGateway.builder().configProvider(provider).build();

        assertThatThrownBy(() -> gateway.send("dbapp", "no-such-channel", req()))
                .isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.send("dbapp", "no-such-channel", req()))
                .isInstanceOf(GatewayException.class);

        assertThat(calls[0]).isEqualTo(2); // 每次 send 都读 provider（对齐支付/发票）
    }

    @Test
    void missingConfigFromProviderShouldFailFast() {
        SmsConfigProvider provider = new InMemorySmsConfigProvider(); // 空 provider
        SmsGateway gateway = SmsGateway.builder().configProvider(provider).build();
        assertThatThrownBy(() -> gateway.send("nope", "alibaba", req()))
                .isInstanceOf(GatewayException.class)
                .satisfies(e -> assertThat(((GatewayException) e).getErrorCode().getCode()).isEqualTo(4103));
    }
}
