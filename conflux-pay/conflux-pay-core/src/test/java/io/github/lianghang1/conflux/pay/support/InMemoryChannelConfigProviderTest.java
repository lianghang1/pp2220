package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryChannelConfigProvider} 行为测试：双索引查找、
 * 账户键自动派生、冲突 fail-fast。
 *
 * @author lianghang
 */
class InMemoryChannelConfigProviderTest {

    /** 最小渠道参数实现：仅承载回调账户键输入 channelAppId。 */
    private static final class TestParams implements ChannelParams {
        private final String channelAppId;

        TestParams(String channelAppId) {
            this.channelAppId = channelAppId;
        }

        @Override public String channelAppId() {
            return channelAppId;
        }
    }

    private static ChannelConfig config(String appId, String channelCode, ChannelParams params, String subMchNo) {
        return new ChannelConfig()
                .setAppId(appId)
                .setChannelCode(channelCode)
                .setSubMchNo(subMchNo)
                .setParams(params);
    }

    @Test
    void lookupByAppIdAndChannel() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("app1", "WECHAT", new TestParams("wx-app-1"), null));

        ChannelConfig found = provider.getConfig("app1", "WECHAT");
        assertThat(found.getAppId()).isEqualTo("app1");

        assertThatThrownBy(() -> provider.getConfig("app1", "ALIPAY"))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_MISSING);
    }

    @Test
    void accountKeyAutoDerivedAndLookup() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("app1", "WECHAT", new TestParams("wx-app-1"), null))
                // 服务商模式：channelAppId#subMchNo 复合账户键
                .register(config("app2", "WECHAT", new TestParams("wx-app-2"), "mch-2001"));

        assertThat(provider.getByAccountKey("WECHAT", "wx-app-1").getAppId()).isEqualTo("app1");
        assertThat(provider.getByAccountKey("WECHAT", "wx-app-2#mch-2001").getAppId()).isEqualTo("app2");

        assertThatThrownBy(() -> provider.getByAccountKey("WECHAT", "no-such-key"))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_MISSING);
    }

    @Test
    void duplicateAccountKeyFailsFast() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("app1", "WECHAT", new TestParams("wx-same"), null));

        assertThatThrownBy(() -> provider.register(config("app2", "WECHAT", new TestParams("wx-same"), null)))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_CONFLICT);
    }

    @Test
    void noParamsSkipsAccountIndex() {
        InMemoryChannelConfigProvider provider = new InMemoryChannelConfigProvider()
                .register(config("app1", "MOCK", null, null));

        assertThat(provider.getConfig("app1", "MOCK")).isNotNull();
        assertThatThrownBy(() -> provider.getByAccountKey("MOCK", "anything"))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_MISSING);
    }
}
