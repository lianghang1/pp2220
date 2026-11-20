package io.github.lianghang1.conflux.pay.autoconfigure;

import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.engine.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;

/**
 * 支付网关自动装配：配置提供者 + 网关门面。
 *
 * <p>内置渠道实现由 core 的 {@code builder().build()} 经 SPI 全量自动注册
 * （引入 channels 依赖即生效，optional SDK 缺失的渠道自动跳过），starter 不再逐个声明
 * 内置渠道 Bean、也不循环注册。使用方仅需声明自定义渠道 Bean（框架未内置的实现），
 * 它们由 {@code List<ChannelProvider>} 收集后交给配置提供者和门面。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PayGatewayProperties.class)
public class PayGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChannelConfigProvider.class)
    public ChannelConfigProvider propertiesChannelConfigProvider(PayGatewayProperties properties,
                                                                 List<ChannelProvider> providers) {
        return new PropertiesChannelConfigProvider(properties, providers);
    }

    @Bean
    @ConditionalOnMissingBean(PaymentGateway.class)
    public PaymentGateway paymentGateway(PayGatewayProperties properties,
                                         List<ChannelProvider> providers,
                                         ObjectProvider<ChannelConfigProvider> configProvider,
                                         ObjectProvider<Clock> clock) {
        ChannelConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 ChannelConfigProvider Bean");
        }

        PaymentGateway.Builder builder = PaymentGateway.builder().configProvider(provider);
        // 仅注册使用方自定义的渠道实现（内置渠道由 builder.build() 经 SPI 自动注册）
        if (providers != null) {
            for (ChannelProvider channelProvider : providers) {
                builder.channel(channelProvider);
            }
        }
        Clock customClock = clock.getIfAvailable();
        if (customClock != null) {
            builder.clock(customClock);
        }

        PaymentGateway gateway = builder.build();
        log.info("PaymentGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
