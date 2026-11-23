package com.sfi.conflux.notify.autoconfigure;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.exception.NotificationErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的短信配置提供者（默认实现）：与支付域
 * {@code PropertiesChannelConfigProvider}、发票域 {@code PropertiesInvoiceConfigProvider}
 * 同构，按 {@code conflux.notify.configs.<configId>.<channelCode>} 读取配置。
 *
 * <p>统一凭证四件套为外层一等字段，厂商特有键按 sms4j Config 字段名声明在 attr；
 * 二者在 {@link NotificationConfig#mergedAttr()} 中合并交装载层。使用方自定义同类型
 * {@link NotificationConfigProvider} Bean 可覆盖。</p>
 *
 * @author lianghang
 */
public class PropertiesNotificationConfigProvider implements NotificationConfigProvider {

    private final NotificationProperties properties;

    public PropertiesNotificationConfigProvider(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public NotificationConfig getConfig(String configId, String channelCode) {
        Map<String, NotificationProperties.ChannelProperties> channels = properties.getConfigs().get(configId);
        if (channels == null || !channels.containsKey(channelCode)) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        NotificationProperties.ChannelProperties props = channels.get(channelCode);
        if (props == null) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING,
                    "短信渠道配置为空: configId={}, channelCode={}", configId, channelCode);
        }
        return toConfig(configId, channelCode, props);
    }

    /** 遍历全部已配置渠道（供启动期预热装载，fail-fast）。 */
    @Override
    public List<NotificationConfig> allConfigs() {
        List<NotificationConfig> result = new ArrayList<NotificationConfig>();
        for (Map.Entry<String, Map<String, NotificationProperties.ChannelProperties>> configEntry
                : properties.getConfigs().entrySet()) {
            String configId = configEntry.getKey();
            Map<String, NotificationProperties.ChannelProperties> channels = configEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, NotificationProperties.ChannelProperties> channelEntry : channels.entrySet()) {
                String channelCode = channelEntry.getKey();
                NotificationProperties.ChannelProperties props = channelEntry.getValue() == null
                        ? new NotificationProperties.ChannelProperties() : channelEntry.getValue();
                result.add(toConfig(configId, channelCode, props));
            }
        }
        return result;
    }

    private static NotificationConfig toConfig(String configId, String channelCode, NotificationProperties.ChannelProperties props) {
        return new NotificationConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode)
                .setAccessKeyId(props.getAccessKeyId())
                .setAccessKeySecret(props.getAccessKeySecret())
                .setSignature(props.getSignature())
                .setTemplateId(props.getTemplateId())
                .setAttr(props.getAttr());
    }
}
