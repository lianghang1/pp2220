package io.github.lianghang1.conflux.sms.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信渠道配置提供者内存实现：与支付域 {@code InMemoryChannelConfigProvider}、
 * 发票域 {@code InMemoryInvoiceConfigProvider} 同构的双索引/注册入口。
 *
 * <p>主要供纯 jar 场景与单测使用；Spring Boot 场景由 starter 提供基于
 * 配置文件的实现。注册即覆盖（配置变更时重新 register 同一 appId+渠道即可）。</p>
 *
 * @author lianghang
 */
public class InMemorySmsConfigProvider implements SmsConfigProvider {

    private final Map<String, SmsChannelConfig> byApp = new ConcurrentHashMap<String, SmsChannelConfig>();

    /** 注册一份渠道配置（同 appId+渠道重复注册即覆盖）。 */
    public InMemorySmsConfigProvider register(SmsChannelConfig config) {
        byApp.put(key(config.getAppId(), config.getChannel()), config);
        return this;
    }

    /** 按 appId + 渠道注册一份键值配置。 */
    public InMemorySmsConfigProvider register(String appId, String channel, Map<String, String> attrs) {
        return register(new SmsChannelConfig(appId, channel, attrs));
    }

    @Override
    public SmsChannelConfig getConfig(String appId, String channel) {
        SmsChannelConfig config = byApp.get(key(appId, channel));
        if (config == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: appId={}, channel={}", appId, channel);
        }
        return config;
    }

    private static String key(String appId, String channel) {
        return appId + "#" + channel;
    }
}
