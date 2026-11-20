package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.engine.SmsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 短信网关自动装配：配置提供者 + 网关门面，与支付/发票 starter 同构。
 *
 * <p>默认提供基于配置文件的 {@link PropertiesSmsConfigProvider}（可被使用方
 * 同类型 Bean 覆盖，如 DB/配置中心实现），注入 {@link SmsGateway}；启动期遍历
 * 已配置渠道预热装载，坏配置 fail-fast。渠道能力全在 sms4j 原始包。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {

    /** 默认配置提供者：读 conflux.sms.configs.*；使用方自定义同类型 Bean 可覆盖。 */
    @Bean
    @ConditionalOnMissingBean(SmsConfigProvider.class)
    public SmsConfigProvider smsConfigProvider(SmsProperties properties) {
        return new PropertiesSmsConfigProvider(properties);
    }

    /**
     * 网关门面：注入配置提供者；收集使用方声明的 {@link SmsChannelProvider} Bean
     * （自定义渠道，按 sms4j 原生 Factory 扩展），内置渠道由 build 预热时自动装载。
     * 与支付/发票 starter 收集 {@code List<ChannelProvider>} 的做法同构。
     */
    @Bean
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway smsGateway(ObjectProvider<SmsConfigProvider> configProvider,
                                 List<SmsChannelProvider> providers) {
        SmsConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 SmsConfigProvider Bean");
        }
        SmsGateway.Builder builder = SmsGateway.builder().configProvider(provider);
        if (providers != null) {
            for (SmsChannelProvider channelProvider : providers) {
                builder.channel(channelProvider);
            }
        }
        SmsGateway gateway = builder.build();
        log.info("SmsGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
