package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.dromara.sms4j.aliyun.config.AlibabaFactory;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.universal.SupplierConfig;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.dromara.sms4j.provider.factory.ProviderFactoryHolder;
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

    private static Map<String, String> alibabaAttr() {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("accessKeyId", "LTAI5tTestKey");
        attr.put("accessKeySecret", "testSecret");
        attr.put("signature", "签名");
        attr.put("templateId", "SMS_123456");
        attr.put("templateName", "code");
        attr.put("regionId", "cn-hangzhou");
        return attr;
    }

    @Test
    void shouldLoadAlibabaConfigIntoFactory() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        String configId = registry.register("demo", "alibaba", alibabaAttr());

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
        registry.register("rebuild", "alibaba", alibabaAttr());
        SmsBlend first = SmsFactory.getSmsBlend("rebuild::alibaba");

        // 相同配置复用（指纹一致）
        registry.register("rebuild", "alibaba", alibabaAttr());
        assertThat(SmsFactory.getSmsBlend("rebuild::alibaba")).isSameAs(first);

        // 配置变更（密钥轮换）触发重建（指纹变化）
        Map<String, String> changed = alibabaAttr();
        changed.put("accessKeySecret", "rotated-secret");
        registry.register("rebuild", "alibaba", changed);
        assertThat(SmsFactory.getSmsBlend("rebuild::alibaba")).isNotSameAs(first);
    }

    @Test
    void differentAppsShouldIsolateConfigId() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        String a = registry.register("appA", "alibaba", alibabaAttr());
        String b = registry.register("appB", "alibaba", alibabaAttr());
        assertThat(a).isNotEqualTo(b);
        assertThat(SmsFactory.getSmsBlend(a)).isNotSameAs(SmsFactory.getSmsBlend(b));
    }

    @Test
    void unknownSupplierShouldFailFast() {
        SmsBlendRegistry registry = new SmsBlendRegistry();
        assertThatThrownBy(() -> registry.register("demo", "not-exist", new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.CHANNEL_NOT_FOUND));
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
    void customFactoryInstanceShouldExtendSuppliers() {
        // 自定义渠道走 sms4j 原生扩展：把 Factory 实例经 SmsChannelProvider 注册。
        // 这里委托官方 AlibabaFactory 实现一个标识为 mycustom 的自定义工厂，验证原生注册路径。
        final AlibabaFactory delegate = AlibabaFactory.instance();
        BaseProviderFactory customFactory = new BaseProviderFactory() {
            @Override
            public SmsBlend createSms(SupplierConfig config) {
                return delegate.createSms((AlibabaConfig) config);
            }
            @Override
            @SuppressWarnings("unchecked")
            public Class getConfigClass() {
                return AlibabaConfig.class;
            }
            @Override
            public String getSupplier() {
                return "mycustom";
            }
        };
        SmsBlendRegistry registry = new SmsBlendRegistry();
        registry.register(SmsChannelProvider.of(customFactory));
        // 工厂已进入 sms4j 原生注册表
        assertThat(ProviderFactoryHolder.requireForSupplier("mycustom")).isNotNull();
        String configId = registry.register("custom", "mycustom", alibabaAttr());
        assertThat(SmsFactory.getSmsBlend(configId)).isNotNull();
    }
}
