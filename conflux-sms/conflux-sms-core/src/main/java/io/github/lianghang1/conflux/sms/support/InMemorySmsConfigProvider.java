package io.github.lianghang1.conflux.sms.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信配置提供者内存实现：与支付域 {@code InMemoryChannelConfigProvider}、
 * 发票域 {@code InMemoryInvoiceConfigProvider} 同构的双索引/注册入口。
 *
 * <p>主要供纯 jar 场景与单测使用；Spring Boot 场景由 starter 提供基于
 * 配置文件的实现。注册即覆盖（配置变更时重新 register 同一 configId+渠道即可）。</p>
 *
 * @author lianghang
 */
public class InMemorySmsConfigProvider implements SmsConfigProvider {

    private final Map<String, SmsChannelConfig> byConfig = new ConcurrentHashMap<String, SmsChannelConfig>();

    /** 注册一份渠道配置（同 configId+渠道重复注册即覆盖）。 */
    public InMemorySmsConfigProvider register(SmsChannelConfig config) {
        byConfig.put(key(config.getConfigId(), config.getChannelCode()), config);
        return this;
    }

    /** 按 configId + 渠道 + 凭证四件套注册一份配置（公共字段一等化，厂商键放 attr）。 */
    public InMemorySmsConfigProvider register(String configId, String channelCode,
                                              String accessKeyId, String accessKeySecret,
                                              String signature, String templateId,
                                              Map<String, String> attr) {
        return register(new SmsChannelConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode)
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setSignature(signature)
                .setTemplateId(templateId)
                .setAttr(attr));
    }

    @Override
    public SmsChannelConfig getConfig(String configId, String channelCode) {
        SmsChannelConfig config = byConfig.get(key(configId, channelCode));
        if (config == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        return config;
    }

    private static String key(String configId, String channelCode) {
        return configId + "#" + channelCode;
    }
}
