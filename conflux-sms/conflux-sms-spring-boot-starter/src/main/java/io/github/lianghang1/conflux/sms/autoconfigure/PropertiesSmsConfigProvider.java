package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigKeys;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于配置文件的短信配置提供者（默认实现）：与支付域
 * {@code PropertiesChannelConfigProvider}、发票域 {@code PropertiesInvoiceConfigProvider}
 * 同构，按 {@code conflux.sms.apps.<appId>.<channel>} 读取配置。
 *
 * <p>统一凭证四件套下沉进 attrs（非空统一键优先），其余字段按 sms4j Config
 * 字段名声明在 attrs。使用方自定义同类型 {@link SmsConfigProvider} Bean 可覆盖。</p>
 *
 * @author lianghang
 */
public class PropertiesSmsConfigProvider implements SmsConfigProvider {

    private final SmsProperties properties;

    public PropertiesSmsConfigProvider(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SmsChannelConfig getConfig(String appId, String channel) {
        Map<String, SmsProperties.ChannelProperties> channels = properties.getApps().get(appId);
        if (channels == null || !channels.containsKey(channel)) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: appId={}, channel={}", appId, channel);
        }
        SmsProperties.ChannelProperties props = channels.get(channel);
        if (props == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置为空: appId={}, channel={}", appId, channel);
        }
        return new SmsChannelConfig(appId, channel, mergeProperties(props));
    }

    /** 遍历全部已配置渠道（供启动期预热装载，fail-fast）。 */
    public java.util.List<SmsChannelConfig> allConfigs() {
        java.util.List<SmsChannelConfig> result = new java.util.ArrayList<SmsChannelConfig>();
        for (Map.Entry<String, Map<String, SmsProperties.ChannelProperties>> appEntry
                : properties.getApps().entrySet()) {
            String appId = appEntry.getKey();
            Map<String, SmsProperties.ChannelProperties> channels = appEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, SmsProperties.ChannelProperties> channelEntry : channels.entrySet()) {
                String channel = channelEntry.getKey();
                SmsProperties.ChannelProperties props = channelEntry.getValue() == null
                        ? new SmsProperties.ChannelProperties() : channelEntry.getValue();
                result.add(new SmsChannelConfig(appId, channel, mergeProperties(props)));
            }
        }
        return result;
    }

    /** 统一键 + attrs 合并为一张配置表，键名与 sms4j Config 字段一致。 */
    private static Map<String, String> mergeProperties(SmsProperties.ChannelProperties props) {
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

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!StringUtils.isBlank(value)) {
            map.put(key, value);
        }
    }
}
