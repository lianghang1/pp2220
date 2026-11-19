package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.config.SmsConfigKeys;
import io.github.lianghang1.conflux.sms.engine.SmsGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信网关自动装配：把 {@code conflux.sms.apps.<appId>.<channel>} 配置装载进
 * sms4j，并暴露 {@link SmsGateway} 单例门面。
 *
 * <p>装配层只做配置装载（统一键下沉 + attrs 合并 → 注册），渠道能力全在
 * sms4j 原始包；使用方自定义同类型 Bean 可覆盖默认门面。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway smsGateway(SmsProperties properties) {
        SmsGateway gateway = SmsGateway.builder().build();
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
                gateway.register(appId, channel, mergeProperties(props));
            }
        }
        return gateway;
    }

    /**
     * 统一凭证字段 + 厂商特有 attrs 合并为一张配置表，键名与 sms4j Config 字段一致。
     * 先放 attrs 再用非空统一键覆盖（外层结构化声明优先）。
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
}
