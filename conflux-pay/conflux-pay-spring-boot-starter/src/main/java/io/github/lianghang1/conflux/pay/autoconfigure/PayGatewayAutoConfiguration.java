package io.github.lianghang1.conflux.pay.autoconfigure;

import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channels.mock.MockChannelProvider;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.engine.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 网关自动装配：渠道提供者收集、默认配置提供者与网关门面。
 *
 * <p>SDK 为无状态渠道聚合层，无需使用方注册存储 Bean；
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
    @ConditionalOnMissingBean(io.github.lianghang1.conflux.pay.channels.wechat.WechatChannelProvider.class)
    @ConditionalOnClass(name = "com.github.binarywang.wxpay.service.WxPayService")
    public io.github.lianghang1.conflux.pay.channels.wechat.WechatChannelProvider wechatChannelProvider() {
        return new io.github.lianghang1.conflux.pay.channels.wechat.WechatChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(io.github.lianghang1.conflux.pay.channels.alipay.AlipayChannelProvider.class)
    @ConditionalOnClass(name = "com.alipay.api.AlipayClient")
    public io.github.lianghang1.conflux.pay.channels.alipay.AlipayChannelProvider alipayChannelProvider() {
        return new io.github.lianghang1.conflux.pay.channels.alipay.AlipayChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayChannelProvider.class)
    public io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayChannelProvider unionPayChannelProvider() {
        return new io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayChannelProvider();
    }

    @Bean
    @ConditionalOnMissingBean(io.github.lianghang1.conflux.pay.channels.paypal.PayPalChannelProvider.class)
    public io.github.lianghang1.conflux.pay.channels.paypal.PayPalChannelProvider payPalChannelProvider() {
        return new io.github.lianghang1.conflux.pay.channels.paypal.PayPalChannelProvider();
    }

    // 其余渠道在各自 SDK 依赖就位后按同样姿势补充条件装配

    @Bean
    @ConditionalOnMissingBean(ChannelConfigProvider.class)
    public ChannelConfigProvider propertiesChannelConfigProvider(PayGatewayProperties properties,
                                                                 List<ChannelProvider> providers) {
        return new PropertiesChannelConfigProvider(properties, providers);
    }

    @Bean
    public PaymentGateway paymentGateway(PayGatewayProperties properties,
                                         List<ChannelProvider> providers,
                                         ObjectProvider<ChannelConfigProvider> configProvider) {
        ChannelConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 ChannelConfigProvider Bean");
        }

        PaymentGateway.Builder builder = PaymentGateway.builder().configProvider(provider);
        for (ChannelProvider channelProvider : providers) {
            builder.channel(channelProvider);
        }

        PaymentGateway gateway = builder.build();
        log.info("PaymentGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
