package io.github.lianghang1.paygateway.autoconfigure;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.ChannelParams;
import io.github.lianghang1.paygateway.channel.ChannelProvider;
import io.github.lianghang1.paygateway.config.ChannelConfigProvider;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link ChannelConfigProvider}：
 * 启动期把 {@code pay-gateway.apps.<appId>.channels.<channel>} 的键值对
 * 经各渠道的 {@code parseParams} 解析为渠道参数对象，并建立
 * 「appId+渠道」与「渠道侧应用标识」两个方向的索引。
 *
 * <p>渠道未实现 {@code parseParams}（返回 null）或配置的渠道未注册时，
 * 对应配置项跳过并告警——该渠道需使用方自行实现配置提供者。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesChannelConfigProvider implements ChannelConfigProvider {

    private final PayGatewayProperties properties;
    private final List<ChannelProvider> providers;

    /** appId+渠道 → 配置 */
    private final Map<String, ChannelConfig> byApp = new HashMap<String, ChannelConfig>();
    /** 渠道编码+渠道侧应用标识 → 配置 */
    private final Map<String, ChannelConfig> byChannelApp = new HashMap<String, ChannelConfig>();

    public PropertiesChannelConfigProvider(PayGatewayProperties properties, List<ChannelProvider> providers) {
        this.properties = properties;
        this.providers = providers;
        buildIndexes();
    }

    private void buildIndexes() {
        Map<String, ChannelProvider> providerByCode = new HashMap<String, ChannelProvider>();
        for (ChannelProvider provider : providers) {
            providerByCode.put(provider.channelCode(), provider);
        }
        for (Map.Entry<String, PayGatewayProperties.AppProperties> appEntry : properties.getApps().entrySet()) {
            String appId = appEntry.getKey();
            for (Map.Entry<String, Map<String, String>> channelEntry : appEntry.getValue().getChannels().entrySet()) {
                String channelCode = channelEntry.getKey();
                Map<String, String> channelProps = channelEntry.getValue() == null
                        ? new HashMap<String, String>() : channelEntry.getValue();
                ChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("渠道[{}]未注册，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                ChannelParams params = provider.parseParams(channelProps);
                if (params == null) {
                    log.warn("渠道[{}]不支持声明式配置，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                ChannelConfig config = new ChannelConfig()
                        .setAppId(appId)
                        .setChannelCode(channelCode)
                        .setSubMchNo(channelProps.get("subMchNo"))
                        .setParams(params);
                byApp.put(appKey(appId, channelCode), config);
                if (params.channelAppId() != null) {
                    byChannelApp.put(appKey(channelCode, params.channelAppId()), config);
                }
            }
        }
        log.info("渠道配置装载完成: apps={}, channelAppIndexes={}", byApp.size(), byChannelApp.size());
    }

    @Override
    public ChannelConfig getConfig(String appId, String channelCode) {
        ChannelConfig config = byApp.get(appKey(appId, channelCode));
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: appId={}, channel={}", appId, channelCode);
        }
        return config;
    }

    @Override
    public ChannelConfig getByChannelApp(String channelCode, String channelAppId) {
        ChannelConfig config = byChannelApp.get(appKey(channelCode, channelAppId));
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道侧应用标识无对应配置: channel={}, channelAppId={}", channelCode, channelAppId);
        }
        return config;
    }

    private String appKey(String left, String right) {
        return left + "::" + right;
    }
}
