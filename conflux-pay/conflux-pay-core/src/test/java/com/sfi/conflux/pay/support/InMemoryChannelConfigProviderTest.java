package com.sfi.conflux.pay.support;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryChannelConfigProvider} 行为测试：configId+渠道查找。
 *
 * @author lianghang
 */
class InMemoryChannelConfigProviderTest {

    private static ChannelConfig config(String configId, String channelCode) {
        return new ChannelConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode);
    }

    @Test
    void lookupByConfigIdAndChannel() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("cfg1", "WECHAT"));

        ChannelConfig found = provider.getConfig("cfg1", "WECHAT");
        assertThat(found.getConfigId()).isEqualTo("cfg1");

        assertThatThrownBy(() -> provider.getConfig("cfg1", "ALIPAY"))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_MISSING);
    }

    @Test
    void multipleConfigsIndependent() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("cfg1", "WECHAT"))
                .register(config("cfg2", "WECHAT"))
                .register(config("cfg1", "ALIPAY"));

        assertThat(provider.getConfig("cfg1", "WECHAT").getConfigId()).isEqualTo("cfg1");
        assertThat(provider.getConfig("cfg2", "WECHAT").getConfigId()).isEqualTo("cfg2");
        assertThat(provider.getConfig("cfg1", "ALIPAY").getChannelCode()).isEqualTo("ALIPAY");
    }
}
