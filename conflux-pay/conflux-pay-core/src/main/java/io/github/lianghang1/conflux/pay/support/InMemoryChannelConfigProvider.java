package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道配置提供者内存实现：以 configId + 渠道编码为唯一索引。
 *
 * <p>配置定位权在使用方：正向调用与回调均由使用方按 configId + channelCode 取配置，
 * 框架不再从回调报文里隐式反查账户键。主要供纯 jar 场景与单测使用；
 * Spring Boot 场景由 starter 提供基于配置文件的实现。</p>
 *
 * @author lianghang
 */
public class InMemoryChannelConfigProvider implements ChannelConfigProvider {

    /** configId + 渠道编码 → 配置。 */
    private final Map<String, ChannelConfig> byConfig = new ConcurrentHashMap<String, ChannelConfig>();

    /** 注册一份渠道配置。 */
    public InMemoryChannelConfigProvider register(ChannelConfig config) {
        byConfig.put(config.getConfigId() + "#" + config.getChannelCode(), config);
        return this;
    }

    @Override
    public ChannelConfig getConfig(String configId, String channelCode) {
        ChannelConfig config = byConfig.get(configId + "#" + channelCode);
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: configId={}, channel={}", configId, channelCode);
        }
        return config;
    }
}
