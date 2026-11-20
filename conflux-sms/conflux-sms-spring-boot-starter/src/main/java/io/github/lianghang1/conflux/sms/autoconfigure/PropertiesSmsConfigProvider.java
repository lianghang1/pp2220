package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的短信配置提供者（默认实现）：与支付域
 * {@code PropertiesChannelConfigProvider}、发票域 {@code PropertiesInvoiceConfigProvider}
 * 同构，按 {@code conflux.sms.configs.<configId>.<channelCode>} 读取配置。
 *
 * <p>统一凭证四件套为外层一等字段，厂商特有键按 sms4j Config 字段名声明在 attr；
 * 二者在 {@link SmsChannelConfig#mergedAttr()} 中合并交装载层。使用方自定义同类型
 * {@link SmsConfigProvider} Bean 可覆盖。</p>
 *
 * @author lianghang
 */
public class PropertiesSmsConfigProvider implements SmsConfigProvider {

    private final SmsProperties properties;

    public PropertiesSmsConfigProvider(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SmsChannelConfig getConfig(String configId, String channelCode) {
        Map<String, SmsProperties.ChannelProperties> channels = properties.getConfigs().get(configId);
        if (channels == null || !channels.containsKey(channelCode)) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        SmsProperties.ChannelProperties props = channels.get(channelCode);
        if (props == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置为空: configId={}, channelCode={}", configId, channelCode);
        }
        return toConfig(configId, channelCode, props);
    }

    /** 遍历全部已配置渠道（供启动期预热装载，fail-fast）。 */
    @Override
    public List<SmsChannelConfig> allConfigs() {
        List<SmsChannelConfig> result = new ArrayList<SmsChannelConfig>();
        for (Map.Entry<String, Map<String, SmsProperties.ChannelProperties>> configEntry
                : properties.getConfigs().entrySet()) {
            String configId = configEntry.getKey();
            Map<String, SmsProperties.ChannelProperties> channels = configEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, SmsProperties.ChannelProperties> channelEntry : channels.entrySet()) {
                String channelCode = channelEntry.getKey();
                SmsProperties.ChannelProperties props = channelEntry.getValue() == null
                        ? new SmsProperties.ChannelProperties() : channelEntry.getValue();
                result.add(toConfig(configId, channelCode, props));
            }
        }
        return result;
    }

    private static SmsChannelConfig toConfig(String configId, String channelCode, SmsProperties.ChannelProperties props) {
        return new SmsChannelConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode)
                .setAccessKeyId(props.getAccessKeyId())
                .setAccessKeySecret(props.getAccessKeySecret())
                .setSignature(props.getSignature())
                .setTemplateId(props.getTemplateId())
                .setAttr(props.getAttr());
    }
}
