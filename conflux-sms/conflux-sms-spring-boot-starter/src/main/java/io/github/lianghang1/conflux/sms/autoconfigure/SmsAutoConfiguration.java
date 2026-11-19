package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channels.aliyun.AliyunChannelProvider;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.engine.SmsGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 短信网关自动装配：渠道提供者收集、默认配置提供者与网关门面。
 *
 * <p>与支付域 PayGatewayAutoConfiguration 同构，全部 Bean 可被使用方同类型 Bean 覆盖。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AliyunChannelProvider.class)
    public AliyunChannelProvider aliyunChannelProvider() {
        return new AliyunChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SmsConfigProvider.class)
    public SmsConfigProvider smsConfigProvider(SmsProperties properties,
                                               ObjectProvider<SmsChannelProvider> providers) {
        return new PropertiesSmsConfigProvider(properties, providers.orderedStream().collect(
                java.util.stream.Collectors.<SmsChannelProvider>toList()));
    }

    @Bean
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway smsGateway(SmsConfigProvider configProvider,
                                 ObjectProvider<SmsChannelProvider> providers) {
        SmsGateway.Builder builder = SmsGateway.builder().configProvider(configProvider);
        for (SmsChannelProvider provider : providers.orderedStream()
                .collect(java.util.stream.Collectors.<SmsChannelProvider>toList())) {
            builder.channel(provider);
        }
        return builder.build();
    }
}
