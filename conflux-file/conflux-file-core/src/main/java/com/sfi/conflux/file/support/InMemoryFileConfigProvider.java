package com.sfi.conflux.file.support;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.file.config.FileChannelConfig;
import com.sfi.conflux.file.config.FileConfigProvider;
import com.sfi.conflux.file.exception.FileErrorCode;
import org.dromara.x.file.storage.core.FileStorageProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件渠道配置提供者内存实现：与支付域 {@code InMemoryChannelConfigProvider}、
 * 发票域 {@code InMemoryInvoiceConfigProvider}、通知域 {@code InMemoryNotificationConfigProvider}
 * 同构的注册入口。
 *
 * <p>主要供纯 jar 场景与单测使用；Spring Boot 场景由 starter 提供基于配置文件的实现。
 * 注册即覆盖（配置变更时重新 register 同一 configId+platform 即可）。</p>
 *
 * @author lianghang
 */
public class InMemoryFileConfigProvider implements FileConfigProvider {

    private final Map<String, FileChannelConfig> byApp = new ConcurrentHashMap<String, FileChannelConfig>();

    /** 注册一份平台配置（同 configId+channelCode 重复注册即覆盖）。 */
    public InMemoryFileConfigProvider register(FileChannelConfig config) {
        byApp.put(key(config.getConfigId(), config.getChannelCode()), config);
        return this;
    }

    /** 按 configId + 平台标识注册一份官方平台配置。 */
    public InMemoryFileConfigProvider register(String configId, String channelCode, FileStorageProperties properties) {
        return register(new FileChannelConfig(configId, channelCode, properties));
    }

    @Override
    public FileChannelConfig getConfig(String configId, String channelCode) {
        FileChannelConfig config = byApp.get(key(configId, channelCode));
        if (config == null) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        return config;
    }

    @Override
    public List<FileChannelConfig> allConfigs() {
        return new ArrayList<FileChannelConfig>(byApp.values());
    }

    private static String key(String configId, String channelCode) {
        return configId + "#" + channelCode;
    }
}
