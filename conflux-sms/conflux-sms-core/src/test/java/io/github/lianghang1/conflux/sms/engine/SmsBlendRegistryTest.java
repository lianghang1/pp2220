package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.config.SmsSupplierFactories;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 装载器测试：验证「渠道标识 + 配置」经 sms4j 原始包装载为 SmsBlend 并注册进 SmsFactory。
 * 全部为本地装载，不触发任何网络发送。
 *
 * @author lianghang
 */
class SmsBlendRegistryTest {

    private static Map<String, String> alibabaAttrs() {
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("accessKeyId", "LTAI5tTestKey");
        attrs.put("accessKeySecret", "testSecret");
        attrs.put("signature", "签名");
        attrs.put("templateId", "SMS_123456");
        attrs.put("templateName", "code");
        attrs.put("regionId", "cn-hangzhou");
        return attrs;
    }

    @Test
    void shouldLoadAlibabaConfigIntoFactory() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        String configId = registry.register("demo", "alibaba", alibabaAttrs());

        assertThat(configId).isEqualTo("demo::alibaba");
        SmsBlend blend = SmsFactory.getSmsBlend(configId);
        assertThat(blend).isNotNull();
        assertThat(blend.getSupplier()).isEqualTo("alibaba");
        assertThat(blend.getConfigId()).isEqualTo("demo::alibaba");
        assertThat(registry.loadedConfigIds()).contains("demo::alibaba");
    }

    @Test
    void configChangeShouldRebuildBlend() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        registry.register("rebuild", "alibaba", alibabaAttrs());
        SmsBlend first = SmsFactory.getSmsBlend("rebuild::alibaba");

        // 相同配置复用（指纹一致）
        registry.register("rebuild", "alibaba", alibabaAttrs());
        assertThat(SmsFactory.getSmsBlend("rebuild::alibaba")).isSameAs(first);

        // 配置变更（密钥轮换）触发重建（指纹变化）
        Map<String, String> changed = alibabaAttrs();
        changed.put("accessKeySecret", "rotated-secret");
        registry.register("rebuild", "alibaba", changed);
        assertThat(SmsFactory.getSmsBlend("rebuild::alibaba")).isNotSameAs(first);
    }

    @Test
    void differentAppsShouldIsolateConfigId() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        String a = registry.register("appA", "alibaba", alibabaAttrs());
        String b = registry.register("appB", "alibaba", alibabaAttrs());
        assertThat(a).isNotEqualTo(b);
        assertThat(SmsFactory.getSmsBlend(a)).isNotSameAs(SmsFactory.getSmsBlend(b));
    }

    @Test
    void unknownSupplierShouldFailFast() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        assertThatThrownBy(() -> registry.register("demo", "not-exist", new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(SmsErrorCode.CHANNEL_NOT_FOUND));
    }

    @Test
    void blankAppOrChannelShouldFailFast() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        assertThatThrownBy(() -> registry.register("", "alibaba", new HashMap<String, String>()))
                .isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> registry.register("demo", "  ", new HashMap<String, String>()))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void customFactoryShouldExtendSuppliers() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        registry.registerFactory("myali", SmsSupplierFactories.factoryClassName("alibaba"));
        String configId = registry.register("custom", "myali", alibabaAttrs());
        assertThat(SmsFactory.getSmsBlend(configId)).isNotNull();
    }
}
