package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channels.aliyun.AliyunChannelProvider;
import io.github.lianghang1.conflux.sms.channels.aliyun.AliyunParams;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.junit.jupiter.api.Test;

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
    void shouldLoadOurAppsStructureIntoChannelParams() {
        SmsProperties props = new SmsProperties();
        SmsProperties.ChannelProperties channel = new SmsProperties.ChannelProperties();
        channel.getAttrs().put("accessKeyId", "LTAI5tTestKey");
        channel.getAttrs().put("accessKeySecret", "testSecret");
        channel.getAttrs().put("signature", "签名");
        channel.getAttrs().put("templateId", "SMS_123456");
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
        assertEquals("SMS_123456", params.getTemplateId());
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
