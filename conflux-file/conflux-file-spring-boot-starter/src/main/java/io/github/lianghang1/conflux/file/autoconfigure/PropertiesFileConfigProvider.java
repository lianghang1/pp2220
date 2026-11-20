package io.github.lianghang1.conflux.file.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.file.config.FileChannelConfig;
import io.github.lianghang1.conflux.file.config.FileConfigProvider;
import io.github.lianghang1.conflux.file.exception.FileErrorCode;
import org.dromara.x.file.storage.core.FileStorageProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的文件配置提供者（默认实现）：与支付域 {@code PropertiesChannelConfigProvider}、
 * 发票域 {@code PropertiesInvoiceConfigProvider}、短信域 {@code PropertiesSmsConfigProvider}
 * 同构，按 {@code conflux.file.configs.<configId>.<channelCode>} 读取配置。
 *
 * <p>每个平台实例的 value 直接是官方 {@link FileStorageProperties}（由 Spring Boot 绑定），
 * 我们原样交给内核，不解析任何平台字段。使用方自定义同类型 {@link FileConfigProvider} Bean
 * 可覆盖。</p>
 *
 * @author lianghang
 */
public class PropertiesFileConfigProvider implements FileConfigProvider {

    private final FileProperties properties;

    public PropertiesFileConfigProvider(FileProperties properties) {
        this.properties = properties;
    }

    @Override
    public FileChannelConfig getConfig(String configId, String channelCode) {
        Map<String, FileStorageProperties> platforms = properties.getConfigs().get(configId);
        if (platforms == null || !platforms.containsKey(channelCode)) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        FileStorageProperties props = platforms.get(channelCode);
        if (props == null) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置为空: configId={}, channelCode={}", configId, channelCode);
        }
        return new FileChannelConfig(configId, channelCode, props);
    }

    @Override
    public List<FileChannelConfig> allConfigs() {
        List<FileChannelConfig> result = new ArrayList<FileChannelConfig>();
        for (Map.Entry<String, Map<String, FileStorageProperties>> appEntry
                : properties.getConfigs().entrySet()) {
            String configId = appEntry.getKey();
            Map<String, FileStorageProperties> platforms = appEntry.getValue();
            if (platforms == null) {
                continue;
            }
            for (Map.Entry<String, FileStorageProperties> platformEntry : platforms.entrySet()) {
                String channelCode = platformEntry.getKey();
                FileStorageProperties props = platformEntry.getValue() == null
                        ? new FileStorageProperties() : platformEntry.getValue();
                result.add(new FileChannelConfig(configId, channelCode, props));
            }
        }
        return result;
    }
}
