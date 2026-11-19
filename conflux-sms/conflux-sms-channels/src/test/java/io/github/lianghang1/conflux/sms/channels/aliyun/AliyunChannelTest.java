package io.github.lianghang1.conflux.sms.channels.aliyun;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.github.lianghang1.conflux.sms.config.SmsConfigKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阿里云短信渠道测试：参数解析（我们的 attrs 规则）、桥接 sms4j AlibabaConfig、能力声明。
 * 不依赖网络与真实凭据。
 *
 * @author lianghang
 */
class AliyunChannelTest {

    private static Map<String, String> attrs() {
        Map<String, String> p = new HashMap<String, String>();
        p.put(ACCESS_KEY_ID, "LTAI5tTestKey");
        p.put(ACCESS_KEY_SECRET, "testSecret");
        p.put(SIGNATURE, "签名");
        p.put(TEMPLATE_ID, "SMS_123456");
        p.put("templateName", "code");
        p.put("regionId", "cn-hangzhou");
        return p;
    }

    @Test
    void paramsShouldParseFromOurAttrs() {
        AliyunParams p = AliyunParams.fromMap(attrs());
        assertThat(p.getAccessKeyId()).isEqualTo("LTAI5tTestKey");
        assertThat(p.getAccessKeySecret()).isEqualTo("testSecret");
        assertThat(p.getSignature()).isEqualTo("签名");
        assertThat(p.getTemplateId()).isEqualTo("SMS_123456");
        assertThat(p.getTemplateName()).isEqualTo("code");
        assertThat(p.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(p.channelAppId()).isEqualTo("LTAI5tTestKey");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> AliyunParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(SmsErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> AliyunParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void shouldBridgeOurParamsToSms4jConfig() {
        AliyunParams p = AliyunParams.fromMap(attrs());
        AlibabaConfig cfg = new AliyunSender().toAlibabaConfig(p);
        // 关键：我们的 attrs 全量映射到 sms4j 官方配置对象
        assertThat(cfg.getAccessKeyId()).isEqualTo("LTAI5tTestKey");
        assertThat(cfg.getAccessKeySecret()).isEqualTo("testSecret");
        assertThat(cfg.getSignature()).isEqualTo("签名");
        assertThat(cfg.getTemplateId()).isEqualTo("SMS_123456");
        assertThat(cfg.getTemplateName()).isEqualTo("code");
        assertThat(cfg.getRegionId()).isEqualTo("cn-hangzhou");
    }

    @Test
    void providerShouldDeclareCodeAndCapabilities() {
        AliyunChannelProvider provider = new AliyunChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("aliyun");
        assertThat(provider.parseParams(attrs())).isInstanceOf(AliyunParams.class);
        assertThat(provider.smsSender()).isInstanceOf(AliyunSender.class);
    }
}
