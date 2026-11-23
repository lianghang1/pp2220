package com.sfi.conflux.sms.autoconfigure;

import com.sfi.conflux.sms.config.SmsChannelConfig;
import com.sfi.conflux.sms.config.SmsConfigProvider;
import com.sfi.conflux.sms.engine.SmsGateway;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.sfi.conflux.sms.config.SmsConfigKeys.ACCESS_KEY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 conflux.sms.configs.<configId>.<channelCode> 配置被 starter 装载进 sms4j SmsFactory，
 * 即「配置规则是我们的、渠道能力来自 sms4j」。不触发真实发送。
 *
 * @author lianghang
 */
class SmsAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private SmsGateway gateway(SmsProperties props) {
        SmsAutoConfiguration auto = new SmsAutoConfiguration();
        SmsConfigProvider provider = auto.smsConfigProvider(props);
        ObjectProvider<SmsConfigProvider> configProvider = mock(ObjectProvider.class);
        when(configProvider.getIfAvailable()).thenReturn(provider);
        return auto.smsGateway(configProvider,
                java.util.Collections.<com.sfi.conflux.sms.channel.SmsChannelProvider>emptyList());
    }

    @Test
    void shouldLoadOurAppsStructureIntoFactory() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        channel.setAccessKeyId("LTAI5tTestKey");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        channel.getAttr().put("templateName", "code");
        channel.getAttr().put("regionId", "cn-hangzhou");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getConfigs().put("demo-boot", channels);

        SmsGateway gw = gateway(props);

        assertThat(gw.registeredChannels()).contains("alibaba");
        SmsBlend blend = SmsFactory.getSmsBlend("demo-boot::alibaba");
        assertThat(blend).isNotNull();
        assertThat(blend.getSupplier()).isEqualTo("alibaba");
    }

    @Test
    void unifiedKeyShouldOverrideSameKeyInAttr() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        channel.getAttr().put(ACCESS_KEY_ID, "attr-old-key");
        channel.setAccessKeyId("unified-new-key");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getConfigs().put("override-app", channels);

        SmsGateway gw = gateway(props);
        assertThat(gw.registeredChannels()).contains("alibaba");
        // 统一键优先：直接查 provider 的合并结果
        SmsConfigProvider provider = new PropertiesSmsConfigProvider(props);
        SmsChannelConfig cfg = provider.getConfig("override-app", "alibaba");
        assertThat(cfg.getAccessKeyId()).isEqualTo("unified-new-key");
        assertThat(cfg.mergedAttr()).containsEntry(ACCESS_KEY_ID, "unified-new-key");
    }

    @Test
    void emptyPropertiesShouldProduceEmptyGateway() {
        SmsGateway gw = gateway(new SmsProperties());
        assertThat(gw.registeredChannels()).isEmpty();
    }
}
