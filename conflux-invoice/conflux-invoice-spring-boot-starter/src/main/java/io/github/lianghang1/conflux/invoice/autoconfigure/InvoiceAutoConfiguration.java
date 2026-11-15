package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.kingdee.KingdeeChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.qixiangyun.QixiangyunChannelProvider;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.engine.InvoiceEventListener;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.engine.InvoiceRuntime;
import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceFileStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;
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
 * 发票网关自动装配：渠道注册、配置提供者、运行时与网关门面。
 *
 * <p>与支付域一致：<b>Store 由使用方实现并注册为 Bean</b>，缺失时启动直接失败并给出
 * 明确提示；渠道、配置提供者、运行时均可被使用方同类型 Bean 覆盖。
 * 四个 REST 渠道不依赖厂商 SDK，默认全部注册；文档未核对的渠道不会暴露可用能力，
 * 需按平台公开文档在 {@code attrs} 中配置路径后启用。</p>
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
    @ConditionalOnMissingBean(KingdeeChannelProvider.class)
    public KingdeeChannelProvider kingdeeChannelProvider() {
        return new KingdeeChannelProvider();
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
    public InvoiceRuntime invoiceRuntime(InvoiceChannelRegistry registry,
                                         InvoiceConfigProvider configProvider,
                                         InvoiceProperties properties,
                                         ObjectProvider<InvoiceStore> invoiceStore,
                                         ObjectProvider<RedApplyStore> redApplyStore,
                                         ObjectProvider<AuthSessionStore> authSessionStore,
                                         ObjectProvider<InvoiceFileStore> fileStore,
                                         ObjectProvider<Clock> clock,
                                         List<InvoiceEventListener> listeners) {
        InvoiceStore store = require(invoiceStore.getIfAvailable(), "InvoiceStore");
        RedApplyStore redStore = require(redApplyStore.getIfAvailable(), "RedApplyStore");
        AuthSessionStore authStore = require(authSessionStore.getIfAvailable(), "AuthSessionStore");
        InvoiceFileStore files = require(fileStore.getIfAvailable(), "InvoiceFileStore");

        InvoiceRuntime runtime = new InvoiceRuntime(registry, configProvider,
                store, redStore, authStore, files);
        // 版式文件固化选项（url/Base64 统一拉平为字节，可选本地落盘）
        runtime.setFileOptions(new io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions()
                .setImmediateDownload(properties.getFile().isImmediateDownload())
                .setOutputDir(properties.getFile().getOutputDir())
                .setDownloadTimeoutMillis(properties.getFile().getDownloadTimeoutMillis()));
        Clock customClock = clock.getIfAvailable();
        if (customClock != null) {
            runtime.setClock(customClock);
        }
        for (InvoiceEventListener listener : listeners) {
            runtime.addListener(listener);
        }
        return runtime;
    }

    @Bean
    public InvoiceGateway invoiceGateway(InvoiceRuntime runtime) {
        log.info("InvoiceGateway 装配完成: channels={}", runtime.registry.registeredChannels());
        return new InvoiceGateway(runtime);
    }

    private static <T> T require(T bean, String name) {
        if (bean == null) {
            throw new IllegalStateException("未找到 " + name
                    + " Bean：请实现该持久化接口并注册为 Spring Bean（参考 core 的 support 内存实现）");
        }
        return bean;
    }
}
