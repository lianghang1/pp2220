package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channel.SmsParams;
import io.github.lianghang1.conflux.sms.config.SmsConfigKeys;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link SmsConfigProvider}：
 * 启动期把 {@code conflux.sms.apps.<appId>.<channel>.attrs} 经各渠道的
 * {@code parseParams} 解析为渠道参数对象，并建立「appId+渠道」索引。
 *
 * <p>与支付域 PropertiesChannelConfigProvider 同构，但短信无回调反查，
 * 故不需要账户键索引。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesSmsConfigProvider implements SmsConfigProvider {

    private final Map<String, SmsChannelConfig> byApp = new HashMap<String, SmsChannelConfig>();

    public PropertiesSmsConfigProvider(SmsProperties properties, List<SmsChannelProvider> providers) {
        buildIndexes(properties, providers);
    }

    private void buildIndexes(SmsProperties properties, List<SmsChannelProvider> providers) {
        Map<String, SmsChannelProvider> providerByCode = new HashMap<String, SmsChannelProvider>();
        for (SmsChannelProvider provider : providers) {
            providerByCode.put(provider.channelCode(), provider);
        }
        for (Map.Entry<String, Map<String, SmsProperties.ChannelProperties>> appEntry
                : properties.getApps().entrySet()) {
            String appId = appEntry.getKey();
            Map<String, SmsProperties.ChannelProperties> channels = appEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, SmsProperties.ChannelProperties> channelEntry : channels.entrySet()) {
                String channelCode = channelEntry.getKey();
                SmsProperties.ChannelProperties channelProps = channelEntry.getValue() == null
                        ? new SmsProperties.ChannelProperties() : channelEntry.getValue();
                SmsChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("短信渠道[{}]未注册，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                SmsParams params = provider.parseParams(mergeProperties(channelProps));
                if (params == null) {
                    log.warn("短信渠道[{}]不支持声明式配置，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                SmsChannelConfig config = new SmsChannelConfig()
                        .setAppId(appId)
                        .setChannelCode(channelCode)
                        .setParams(params);
                byApp.put(appKey(appId, channelCode), config);
            }
        }
        log.info("短信渠道配置装载完成: appChannelConfigs={}", byApp.size());
    }

    /**
     * 把统一凭证字段下沉进 attrs，合并为渠道 parseParams 的入参。
     *
     * <p>合并策略：先放 attrs（兼容「全部塞 attrs」的旧写法与厂商特有键），
     * 再用非空统一键覆盖同名键——外层结构化字段是显式声明，优先级更高。
     * 渠道 provider 因此无需关心凭证来自哪一层。</p>
     */
    private Map<String, String> mergeProperties(SmsProperties.ChannelProperties props) {
        Map<String, String> merged = new LinkedHashMap<String, String>();
        if (props.getAttrs() != null) {
            merged.putAll(props.getAttrs());
        }
        putIfNotBlank(merged, SmsConfigKeys.ACCESS_KEY_ID, props.getAccessKeyId());
        putIfNotBlank(merged, SmsConfigKeys.ACCESS_KEY_SECRET, props.getAccessKeySecret());
        putIfNotBlank(merged, SmsConfigKeys.SIGNATURE, props.getSignature());
        putIfNotBlank(merged, SmsConfigKeys.TEMPLATE_ID, props.getTemplateId());
        return merged;
    }

    private void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!StringUtils.isBlank(value)) {
            map.put(key, value);
        }
    }

    @Override
    public SmsChannelConfig getConfig(String appId, String channelCode) {
        SmsChannelConfig config = byApp.get(appKey(appId, channelCode));
        if (config == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: appId={}, channel={}", appId, channelCode);
        }
        return config;
    }

    private String appKey(String left, String right) {
        return left + "::" + right;
    }
}
