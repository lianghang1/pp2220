package com.sfi.conflux.notify.autoconfigure;

import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.engine.NotificationGateway;
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
 * <p>默认提供基于配置文件的 {@link PropertiesNotificationConfigProvider}（可被使用方
 * 同类型 Bean 覆盖，如 DB/配置中心实现），注入 {@link NotificationGateway}；启动期遍历
 * 已配置渠道预热装载，坏配置 fail-fast。渠道能力全在 sms4j 原始包。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationAutoConfiguration {

    /** 默认配置提供者：读 conflux.notify.configs.*；使用方自定义同类型 Bean 可覆盖。 */
    @Bean
    @ConditionalOnMissingBean(NotificationConfigProvider.class)
    public NotificationConfigProvider smsConfigProvider(NotificationProperties properties) {
        return new PropertiesNotificationConfigProvider(properties);
    }

    /**
     * 网关门面：注入配置提供者；收集使用方声明的 {@link SmsChannelProvider} Bean
     * （自定义渠道，按 sms4j 原生 Factory 扩展），内置渠道由 build 预热时自动装载。
     * 与支付/发票 starter 收集 {@code List<ChannelProvider>} 的做法同构。
     */
    @Bean
    @ConditionalOnMissingBean(NotificationGateway.class)
    public NotificationGateway smsGateway(ObjectProvider<NotificationConfigProvider> configProvider,
                                 List<SmsChannelProvider> providers) {
        NotificationConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 NotificationConfigProvider Bean");
        }
        NotificationGateway.Builder builder = NotificationGateway.builder().configProvider(provider);
        if (providers != null) {
            for (SmsChannelProvider channelProvider : providers) {
                builder.channel(channelProvider);
            }
        }
        NotificationGateway gateway = builder.build();
        log.info("NotificationGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
