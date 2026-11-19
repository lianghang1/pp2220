package io.github.lianghang1.conflux.sms.channel;

import io.github.lianghang1.conflux.sms.config.SmsSupplierFactories;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;

/**
 * 短信渠道声明对象：与支付域 {@code ChannelProvider}、发票域 {@code InvoiceChannelProvider}
 * 处于同一层——承载一个 sms4j 原生 {@link BaseProviderFactory} 实例。
 *
 * <p>渠道的实现（SmsBlend / SupplierConfig / Factory 三接口）全部按
 * <a href="https://sms4j.com/doc3/custom.html">sms4j 官方文档</a>由 sms4j 提供或由使用方
 * 自行扩展，本类只是把 Factory 实例包装成三域统一的 Provider 形态：注册时经 sms4j 原生
 * 入口 {@code ProviderFactoryHolder.registerFactory(factory)} 注入，不含任何渠道业务代码。</p>
 *
 * @author lianghang
 */
public final class SmsChannelProvider {

    private final BaseProviderFactory<?, ?> factory;

    private SmsChannelProvider(BaseProviderFactory<?, ?> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("sms4j Factory 不能为空");
        }
        this.factory = factory;
    }

    /** 声明一个 sms4j 内置渠道（工厂取内置清单，等价于官方 starter 的 registerDefaultFactory）。 */
    public static SmsChannelProvider of(String channelCode) {
        BaseProviderFactory<?, ?> factory = SmsSupplierFactories.factoryInstance(channelCode);
        if (factory == null) {
            throw new IllegalArgumentException("未知的内置短信渠道标识: " + channelCode);
        }
        return new SmsChannelProvider(factory);
    }

    /**
     * 声明一个自定义渠道：直接接收使用方按 sms4j 文档实现的 Factory 实例。
     * 内置清单外的厂商走此入口，无需改动框架代码。
     */
    public static SmsChannelProvider of(BaseProviderFactory<?, ?> factory) {
        return new SmsChannelProvider(factory);
    }

    /** 渠道标识（取 sms4j Factory 的 supplier，三者实现必须一致）。 */
    public String channelCode() {
        return factory.getSupplier();
    }

    /** sms4j 原生 Factory 实例，供注册进 ProviderFactoryHolder。 */
    public BaseProviderFactory<?, ?> factory() {
        return factory;
    }

    @Override
    public String toString() {
        return "SmsChannelProvider{channelCode='" + channelCode() + "'}";
    }
}
