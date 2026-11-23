package com.sfi.conflux.pay.autoconfigure;

import com.sfi.conflux.pay.channel.BuiltinChannels;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelProvider;
import com.sfi.conflux.pay.config.ChannelConfigProvider;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link ChannelConfigProvider}：
 * 启动期把 {@code conflux.pay.configs.<configId>.<channelCode>} 的键值对
 * 经各渠道的 {@code parseParams} 解析为渠道参数对象，建立 configId+渠道 索引。
 *
 * <p>配置定位权在使用方：正向调用与回调均按 configId + channelCode 取配置，
 * 框架不再从回调报文隐式反查账户键。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesChannelConfigProvider implements ChannelConfigProvider {

    private final PayGatewayProperties properties;
    private final List<ChannelProvider> providers;

    /** configId+渠道 → 配置。 */
    private final Map<String, ChannelConfig> byConfig = new HashMap<String, ChannelConfig>();

    public PropertiesChannelConfigProvider(PayGatewayProperties properties, List<ChannelProvider> providers) {
        this.properties = properties;
        this.providers = providers;
        buildIndexes();
    }

    private void buildIndexes() {
        Map<String, ChannelProvider> providerByCode = new HashMap<String, ChannelProvider>();
        // 内置渠道（SPI 清单）先入表，自定义渠道后入表覆盖——与 registry 的"自定义优先"一致
        for (ChannelProvider provider : BuiltinChannels.all()) {
            providerByCode.put(provider.channelCode(), provider);
        }
        if (providers != null) {
            for (ChannelProvider provider : providers) {
                providerByCode.put(provider.channelCode(), provider);
            }
        }
        for (Map.Entry<String, Map<String, PayGatewayProperties.ChannelProperties>> configEntry
                : properties.getConfigs().entrySet()) {
            String configId = configEntry.getKey();
            Map<String, PayGatewayProperties.ChannelProperties> channels = configEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, PayGatewayProperties.ChannelProperties> channelEntry
                    : channels.entrySet()) {
                String channelCode = channelEntry.getKey();
                PayGatewayProperties.ChannelProperties channelProps = channelEntry.getValue() == null
                        ? new PayGatewayProperties.ChannelProperties() : channelEntry.getValue();
                ChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("渠道[{}]未注册，跳过配置[{}]的该渠道配置", channelCode, configId);
                    continue;
                }
                ChannelConfig config = provider.parseParams(channelProps.getAttr());
                if (config == null) {
                    log.warn("渠道[{}]不支持声明式配置，跳过配置[{}]的该渠道配置", channelCode, configId);
                    continue;
                }
                // 统一键 + 个性 attr 收拢到渠道 config 的 attr；subMchNo 为渠道个性键，下沉进 attr（getSubMchNo 从 attr 派生）
                Map<String, String> mergedAttr = new LinkedHashMap<String, String>();
                if (channelProps.getAttr() != null) {
                    mergedAttr.putAll(channelProps.getAttr());
                }
                if (StringUtils.isNotBlank(channelProps.getSubMchNo())) {
                    mergedAttr.put("subMchNo", channelProps.getSubMchNo());
                }
                config.setAttr(mergedAttr)
                        .setConfigId(configId)
                        .setChannelCode(channelCode)
                        .setNotifyUrl(channelProps.getNotifyUrl())
                        .setSandbox(channelProps.isSandbox());
                byConfig.put(appKey(configId, channelCode), config);
            }
        }
        log.info("渠道配置装载完成: configChannelConfigs={}", byConfig.size());
    }

    @Override
    public ChannelConfig getConfig(String configId, String channelCode) {
        ChannelConfig config = byConfig.get(appKey(configId, channelCode));
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: configId={}, channel={}", configId, channelCode);
        }
        return config;
    }

    private String appKey(String configId, String channelCode) {
        return configId + "::" + channelCode;
    }
}
