package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channels.aliyun.AliyunChannelProvider;
import io.github.lianghang1.conflux.sms.channels.aliyun.AliyunParams;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.junit.jupiter.api.Test;

import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.ACCESS_KEY_ID;
import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.ACCESS_KEY_SECRET;
import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.SIGNATURE;
import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.TEMPLATE_ID;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 conflux.sms.apps.<appId>.<channel>.attrs 配置规则能被装载为渠道参数对象，
 * 即「配置规则是我们的」。不触发真实发送。
 *
 * @author lianghang
 */
class PropertiesSmsConfigProviderTest {

    @Test
    void shouldLoadUnifiedKeysIntoChannelParams() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        // 统一键：外层结构化字段
        channel.setAccessKeyId("LTAI5tTestKey");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        // 厂商特有键：仍走 attrs
        channel.getAttrs().put("templateName", "code");
        channel.getAttrs().put("regionId", "cn-hangzhou");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("aliyun", channel);
        props.getApps().put("demo", channels);

        PropertiesSmsConfigProvider provider = new PropertiesSmsConfigProvider(props,
                Collections.singletonList(new AliyunChannelProvider()));

        SmsChannelConfig config = provider.getConfig("demo", "aliyun");
        assertEquals("demo", config.getAppId());
        assertEquals("aliyun", config.getChannelCode());
        AliyunParams params = (AliyunParams) config.getParams();
        assertEquals("LTAI5tTestKey", params.getAccessKeyId());
        assertEquals("testSecret", params.getAccessKeySecret());
        assertEquals("签名", params.getSignature());
        assertEquals("SMS_123456", params.getTemplateId());
        assertEquals("code", params.getTemplateName());
        assertEquals("cn-hangzhou", params.getRegionId());
    }

    @Test
    void unifiedKeyShouldOverrideSameKeyInAttrs() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        // attrs 里放旧写法的同名键，外层统一键应覆盖它
        channel.getAttrs().put(ACCESS_KEY_ID, "attrs-old-key");
        channel.setAccessKeyId("unified-new-key");
        channel.setAccessKeySecret("testSecret");
        channel.setSignature("签名");
        channel.setTemplateId("SMS_123456");
        Map<String, SmsProperties.ChannelProperties> channels = new LinkedHashMap<String, SmsProperties.ChannelProperties>();
        channels.put("aliyun", channel);
        props.getApps().put("demo", channels);

        PropertiesSmsConfigProvider provider = new PropertiesSmsConfigProvider(props,
                Collections.singletonList(new AliyunChannelProvider()));
        AliyunParams params = (AliyunParams) provider.getConfig("demo", "aliyun").getParams();
        assertEquals("unified-new-key", params.getAccessKeyId());
    }

    @Test
    void missingConfigShouldFailFast() {
        PropertiesSmsConfigProvider provider = new PropertiesSmsConfigProvider(new SmsProperties(),
                Collections.singletonList(new AliyunChannelProvider()));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> provider.getConfig("demo", "aliyun"));
        assertSame(SmsErrorCode.CONFIG_MISSING, ex.getErrorCode());
    }
}
