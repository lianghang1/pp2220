package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.engine.SmsGateway;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.ACCESS_KEY_ID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 conflux.sms.apps.<appId>.<channel> 配置被 starter 装载进 sms4j SmsFactory，
 * 即「配置规则是我们的、渠道能力来自 sms4j」。不触发真实发送。
 *
 * @author lianghang
 */
class SmsAutoConfigurationTest {

    private SmsGateway gateway(SmsProperties props) {
        SmsAutoConfiguration auto = new SmsAutoConfiguration();
        SmsConfigProvider provider = auto.smsConfigProvider(props);
        return auto.smsGateway(provider, java.util.Collections.<io.github.lianghang1.conflux.sms.channel.SmsChannelProvider>emptyList());
    }

    @Test
    void shouldLoadOurAppsStructureIntoFactory() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        channel.setAccessKeyId("LTAI5tTestKey");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        channel.getAttrs().put("templateName", "code");
        channel.getAttrs().put("regionId", "cn-hangzhou");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getApps().put("demo-boot", channels);

        SmsGateway gw = gateway(props);

        assertThat(gw.registeredChannels()).contains("alibaba");
        SmsBlend blend = SmsFactory.getSmsBlend("demo-boot::alibaba");
        assertThat(blend).isNotNull();
        assertThat(blend.getSupplier()).isEqualTo("alibaba");
    }

    @Test
    void unifiedKeyShouldOverrideSameKeyInAttrs() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        channel.getAttrs().put(ACCESS_KEY_ID, "attrs-old-key");
        channel.setAccessKeyId("unified-new-key");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getApps().put("override-app", channels);

        SmsGateway gw = gateway(props);
        assertThat(gw.registeredChannels()).contains("alibaba");
        // 统一键优先：直接查 provider 的合并结果
        SmsConfigProvider provider = new PropertiesSmsConfigProvider(props);
        SmsChannelConfig cfg = provider.getConfig("override-app", "alibaba");
        assertThat(cfg.getAttrs()).containsEntry(ACCESS_KEY_ID, "unified-new-key");
    }

    @Test
    void emptyPropertiesShouldProduceEmptyGateway() {
        SmsGateway gw = gateway(new SmsProperties());
        assertThat(gw.registeredChannels()).isEmpty();
    }
}
