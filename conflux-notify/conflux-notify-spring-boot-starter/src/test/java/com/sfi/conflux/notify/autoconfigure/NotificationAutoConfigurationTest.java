package com.sfi.conflux.notify.autoconfigure;

import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.engine.NotificationGateway;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.sfi.conflux.notify.config.NotificationConfigKeys.ACCESS_KEY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 conflux.notify.configs.<configId>.<channelCode> 配置被 starter 装载进 sms4j SmsFactory，
 * 即「配置规则是我们的、渠道能力来自 sms4j」。不触发真实发送。
 *
 * @author lianghang
 */
class NotificationAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private NotificationGateway gateway(NotificationProperties props) {
        NotificationAutoConfiguration auto = new NotificationAutoConfiguration();
        NotificationConfigProvider provider = auto.smsConfigProvider(props);
        ObjectProvider<NotificationConfigProvider> configProvider = mock(ObjectProvider.class);
        when(configProvider.getIfAvailable()).thenReturn(provider);
        return auto.smsGateway(configProvider,
                java.util.Collections.<com.sfi.conflux.notify.channel.SmsChannelProvider>emptyList());
    }

    @Test
    void shouldLoadOurAppsStructureIntoFactory() {
        NotificationProperties props = new NotificationProperties();
        NotificationProperties.ChannelProperties channel = new NotificationProperties.ChannelProperties();
        channel.setAccessKeyId("LTAI5tTestKey");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        channel.getAttr().put("templateName", "code");
        channel.getAttr().put("regionId", "cn-hangzhou");
        Map<String, NotificationProperties.ChannelProperties> channels = new LinkedHashMap<String, NotificationProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getConfigs().put("demo-boot", channels);

        NotificationGateway gw = gateway(props);

        assertThat(gw.registeredChannels()).contains("alibaba");
        SmsBlend blend = SmsFactory.getSmsBlend("demo-boot::alibaba");
        assertThat(blend).isNotNull();
        assertThat(blend.getSupplier()).isEqualTo("alibaba");
    }

    @Test
    void unifiedKeyShouldOverrideSameKeyInAttr() {
        NotificationProperties props = new NotificationProperties();
        NotificationProperties.ChannelProperties channel = new NotificationProperties.ChannelProperties();
        channel.getAttr().put(ACCESS_KEY_ID, "attr-old-key");
        channel.setAccessKeyId("unified-new-key");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        Map<String, NotificationProperties.ChannelProperties> channels = new LinkedHashMap<String, NotificationProperties.ChannelProperties>();
        channels.put("alibaba", channel);
        props.getConfigs().put("override-app", channels);

        NotificationGateway gw = gateway(props);
        assertThat(gw.registeredChannels()).contains("alibaba");
        // 统一键优先：直接查 provider 的合并结果
        NotificationConfigProvider provider = new PropertiesNotificationConfigProvider(props);
        NotificationConfig cfg = provider.getConfig("override-app", "alibaba");
        assertThat(cfg.getAccessKeyId()).isEqualTo("unified-new-key");
        assertThat(cfg.mergedAttr()).containsEntry(ACCESS_KEY_ID, "unified-new-key");
    }

    @Test
    void builtinImChannelsShouldBeRegisteredViaSpi() {
        // 内置 IM 渠道经 SPI 清单在 build 期自动注册，无需任何配置、无需 .channel()
        NotificationGateway gw = gateway(new NotificationProperties());
        assertThat(gw.registeredChannels()).contains(
                "wechat_mp", "wechat_mini", "wechat_wecom",
                "alipay_mini", "alipay_public", "feishu");
    }
}
