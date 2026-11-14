package io.github.lianghang1.paygateway.autoconfigure;

import io.github.lianghang1.paygateway.channel.ChannelProvider;
import io.github.lianghang1.paygateway.channels.mock.MockChannelProvider;
import io.github.lianghang1.paygateway.config.ChannelConfigProvider;
import io.github.lianghang1.paygateway.engine.DownstreamNotifier;
import io.github.lianghang1.paygateway.engine.HttpDownstreamNotifier;
import io.github.lianghang1.paygateway.engine.PaymentGateway;
import io.github.lianghang1.paygateway.engine.RetryPolicy;
import io.github.lianghang1.paygateway.event.PaymentEventListener;
import io.github.lianghang1.paygateway.store.NotifyLogStore;
import io.github.lianghang1.paygateway.store.PaymentStore;
import io.github.lianghang1.paygateway.store.ProfitShareStore;
import io.github.lianghang1.paygateway.store.ReconDiscrepancyStore;
import io.github.lianghang1.paygateway.store.RefundStore;
import io.github.lianghang1.paygateway.store.TransferStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关自动装配：渠道提供者收集、默认配置提供者、通知投递器与网关门面。
 *
 * <p>使用方需自行实现并注册 Store Bean（支付单存储必填，其余按需）；
 * 渠道按类路径与 Bean 存在性激活，全部可被使用方同类型 Bean 覆盖。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PayGatewayProperties.class)
public class PayGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MockChannelProvider.class)
    public MockChannelProvider mockChannelProvider() {
        return new MockChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(io.github.lianghang1.paygateway.channels.wechat.WechatChannelProvider.class)
    @ConditionalOnClass(name = "com.wechat.pay.java.core.RSAAutoCertificateConfig")
    public io.github.lianghang1.paygateway.channels.wechat.WechatChannelProvider wechatChannelProvider() {
        return new io.github.lianghang1.paygateway.channels.wechat.WechatChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(io.github.lianghang1.paygateway.channels.alipay.AlipayChannelProvider.class)
    @ConditionalOnClass(name = "com.alipay.api.AlipayClient")
    public io.github.lianghang1.paygateway.channels.alipay.AlipayChannelProvider alipayChannelProvider() {
        return new io.github.lianghang1.paygateway.channels.alipay.AlipayChannelProvider();
    }

    // 其余渠道在各自 SDK 依赖就位后按同样姿势补充条件装配

    @Bean
    @ConditionalOnMissingBean(ChannelConfigProvider.class)
    public ChannelConfigProvider propertiesChannelConfigProvider(PayGatewayProperties properties,
                                                                 List<ChannelProvider> providers) {
        return new PropertiesChannelConfigProvider(properties, providers);
    }

    @Bean
    @ConditionalOnMissingBean(DownstreamNotifier.class)
    @ConditionalOnBean(NotifyLogStore.class)
    public DownstreamNotifier downstreamNotifier(NotifyLogStore notifyLogStore, PayGatewayProperties properties) {
        return new HttpDownstreamNotifier(notifyLogStore,
                new RetryPolicy(properties.getNotify().retrySecondsArray()),
                properties.getNotify().getSecret(),
                properties.getNotify().getTimeoutMillis(),
                Clock.systemDefaultZone());
    }

    @Bean
    public PaymentGateway paymentGateway(PayGatewayProperties properties,
                                         List<ChannelProvider> providers,
                                         ObjectProvider<ChannelConfigProvider> configProvider,
                                         ObjectProvider<PaymentStore> paymentStore,
                                         ObjectProvider<RefundStore> refundStore,
                                         ObjectProvider<TransferStore> transferStore,
                                         ObjectProvider<ProfitShareStore> profitShareStore,
                                         ObjectProvider<NotifyLogStore> notifyLogStore,
                                         ObjectProvider<ReconDiscrepancyStore> reconDiscrepancyStore,
                                         ObjectProvider<DownstreamNotifier> notifier,
                                         List<PaymentEventListener> listeners) {
        PaymentStore store = paymentStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("未找到 PaymentStore Bean：请实现支付单存储并注册为 Spring Bean");
        }
        ChannelConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 ChannelConfigProvider Bean");
        }

        PaymentGateway.Builder builder = PaymentGateway.builder()
                .configProvider(provider)
                .paymentStore(store);
        for (ChannelProvider channelProvider : providers) {
            builder.channel(channelProvider);
        }
        RefundStore refund = refundStore.getIfAvailable();
        if (refund != null) {
            builder.refundStore(refund);
        }
        TransferStore transfer = transferStore.getIfAvailable();
        if (transfer != null) {
            builder.transferStore(transfer);
        }
        ProfitShareStore share = profitShareStore.getIfAvailable();
        if (share != null) {
            builder.profitShareStore(share);
        }
        NotifyLogStore notifyLog = notifyLogStore.getIfAvailable();
        if (notifyLog != null) {
            builder.notifyLogStore(notifyLog);
        }
        ReconDiscrepancyStore recon = reconDiscrepancyStore.getIfAvailable();
        if (recon != null) {
            builder.reconDiscrepancyStore(recon);
        }
        DownstreamNotifier customNotifier = notifier.getIfAvailable();
        if (customNotifier != null && notifyLog != null) {
            builder.notifier(customNotifier);
        }
        for (PaymentEventListener listener : listeners) {
            builder.listener(listener);
        }
        builder.notifyRetrySeconds(properties.getNotify().retrySecondsArray())
                .notifySecret(properties.getNotify().getSecret())
                .notifyHttpTimeoutMillis(properties.getNotify().getTimeoutMillis());

        PaymentGateway gateway = builder.build();
        log.info("PaymentGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
