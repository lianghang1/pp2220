package com.sfi.conflux.invoice.autoconfigure;

import com.sfi.conflux.invoice.channel.InvoiceChannelProvider;
import com.sfi.conflux.invoice.config.InvoiceConfigProvider;
import com.sfi.conflux.invoice.config.InvoiceFileOptions;
import com.sfi.conflux.invoice.engine.InvoiceGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;

/**
 * 发票网关自动装配：配置提供者 + 网关门面。
 *
 * <p>内置渠道实现由 core 的 {@code builder().build()} 经 SPI 全量自动注册
 * （引入 channels 依赖即生效，可选依赖缺失的渠道自动跳过），starter 不再逐个声明内置渠道
 * Bean、也不循环注册。使用方仅需声明自定义渠道 Bean（框架未内置的实现）。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(InvoiceProperties.class)
public class InvoiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InvoiceConfigProvider.class)
    public InvoiceConfigProvider propertiesInvoiceConfigProvider(
            InvoiceProperties properties, List<InvoiceChannelProvider> providers) {
        return new PropertiesInvoiceConfigProvider(properties, providers);
    }

    @Bean
    @ConditionalOnMissingBean(InvoiceGateway.class)
    public InvoiceGateway invoiceGateway(InvoiceProperties properties,
                                         List<InvoiceChannelProvider> providers,
                                         ObjectProvider<InvoiceConfigProvider> configProvider,
                                         ObjectProvider<Clock> clock) {
        InvoiceConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 InvoiceConfigProvider Bean");
        }

        InvoiceProperties.FileProperties fileProps = properties.getFile();
        InvoiceFileOptions fileOptions = new InvoiceFileOptions()
                .setImmediateDownload(fileProps.isImmediateDownload())
                .setOutputDir(fileProps.getOutputDir())
                .setDownloadTimeoutMillis(fileProps.getDownloadTimeoutMillis());

        InvoiceGateway.Builder builder = InvoiceGateway.builder()
                .configProvider(provider)
                .fileOptions(fileOptions);
        // 仅注册使用方自定义的渠道实现（内置渠道由 builder.build() 经 SPI 自动注册）
        if (providers != null) {
            for (InvoiceChannelProvider channelProvider : providers) {
                builder.channel(channelProvider);
            }
        }
        Clock customClock = clock.getIfAvailable();
        if (customClock != null) {
            builder.clock(customClock);
        }

        InvoiceGateway gateway = builder.build();
        log.info("InvoiceGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
