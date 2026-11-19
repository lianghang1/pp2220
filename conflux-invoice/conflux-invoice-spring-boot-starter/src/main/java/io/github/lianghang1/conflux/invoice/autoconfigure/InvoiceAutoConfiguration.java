package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.qixiangyun.QixiangyunChannelProvider;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;

/**
 * 发票网关自动装配：渠道注册、配置提供者与网关门面。
 *
 * <p>SDK 为无状态渠道聚合层，无需使用方注册任何存储 Bean；
 * 渠道、配置提供者均可被使用方同类型 Bean 覆盖。REST 渠道默认全部注册。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(InvoiceProperties.class)
public class InvoiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MockInvoiceChannelProvider.class)
    @ConditionalOnProperty(prefix = "conflux.invoice", name = "mock-enabled",
            havingValue = "true", matchIfMissing = true)
    public MockInvoiceChannelProvider mockInvoiceChannelProvider() {
        return new MockInvoiceChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(QixiangyunChannelProvider.class)
    public QixiangyunChannelProvider qixiangyunChannelProvider() {
        return new QixiangyunChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(BaiwangJsChannelProvider.class)
    public BaiwangJsChannelProvider baiwangJsChannelProvider() {
        return new BaiwangJsChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(
            io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongChannelProvider.class)
    public io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongChannelProvider
            piaotongChannelProvider() {
        return new io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(BaiwangChannelProvider.class)
    public BaiwangChannelProvider baiwangChannelProvider() {
        return new BaiwangChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(NuonuoChannelProvider.class)
    public NuonuoChannelProvider nuonuoChannelProvider() {
        return new NuonuoChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(InvoiceChannelRegistry.class)
    public InvoiceChannelRegistry invoiceChannelRegistry(List<InvoiceChannelProvider> providers) {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        for (InvoiceChannelProvider provider : providers) {
            registry.register(provider);
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(InvoiceConfigProvider.class)
    public InvoiceConfigProvider propertiesInvoiceConfigProvider(
            InvoiceProperties properties, List<InvoiceChannelProvider> providers) {
        return new PropertiesInvoiceConfigProvider(properties, providers);
    }

    @Bean
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
        for (InvoiceChannelProvider channelProvider : providers) {
            builder.channel(channelProvider);
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
