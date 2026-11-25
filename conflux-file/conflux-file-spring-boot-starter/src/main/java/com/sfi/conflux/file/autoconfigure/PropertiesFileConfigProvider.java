package com.sfi.conflux.file.autoconfigure;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.file.config.FileChannelConfig;
import com.sfi.conflux.file.config.FileConfigProvider;
import com.sfi.conflux.file.exception.FileErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的文件配置提供者（默认实现）：与支付域 {@code PropertiesChannelConfigProvider}、
 * 发票域、通知域、登录域同构，按 {@code conflux.file.configs.<configId>.<channelCode>} 读取配置。
 *
 * <p>每个 channelCode（= 官方平台段名）的 value 是自有扁平 {@link FileProperties.ChannelProperties}，
 * 原样转成 {@link FileChannelConfig} 交给内核反射映射。使用方自定义同类型 {@link FileConfigProvider}
 * Bean 可覆盖。</p>
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
        Map<String, FileProperties.ChannelProperties> platforms = properties.getConfigs().get(configId);
        if (platforms == null || !platforms.containsKey(channelCode)) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        FileProperties.ChannelProperties props = platforms.get(channelCode);
        if (props == null) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置为空: configId={}, channelCode={}", configId, channelCode);
        }
        return new FileChannelConfig(configId, channelCode, props.getAttr());
    }

    @Override
    public List<FileChannelConfig> allConfigs() {
        List<FileChannelConfig> result = new ArrayList<FileChannelConfig>();
        for (Map.Entry<String, Map<String, FileProperties.ChannelProperties>> appEntry
                : properties.getConfigs().entrySet()) {
            String configId = appEntry.getKey();
            Map<String, FileProperties.ChannelProperties> platforms = appEntry.getValue();
            if (platforms == null) {
                continue;
            }
            for (Map.Entry<String, FileProperties.ChannelProperties> platformEntry : platforms.entrySet()) {
                String channelCode = platformEntry.getKey();
                FileProperties.ChannelProperties props = platformEntry.getValue();
                result.add(new FileChannelConfig(configId, channelCode,
                        props == null ? null : props.getAttr()));
            }
        }
        return result;
    }
}
