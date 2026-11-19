package io.github.lianghang1.conflux.sms.channel;

import io.github.lianghang1.conflux.sms.config.SmsSupplierFactories;

/**
 * 短信渠道声明对象：与支付域 {@code ChannelProvider}、发票域 {@code InvoiceChannelProvider}
 * 处于同一层——只声明「渠道是谁、用哪个 sms4j 工厂装载」。
 *
 * <p>渠道的配置对象、签名、请求、响应、发送能力全部来自 sms4j 原始包，本类不含任何
 * 渠道实现；配置键值仍由 {@code SmsConfigProvider} 按 appId + channel 读取，与支付/发票
 * 「Provider 声明渠道、configProvider 提供配置」的对外形态一致。</p>
 *
 * @author lianghang
 */
public final class SmsChannelProvider {

    private final String channelCode;
    private final String factoryClassName;

    private SmsChannelProvider(String channelCode, String factoryClassName) {
        this.channelCode = channelCode;
        this.factoryClassName = factoryClassName;
    }

    /** 声明一个内置 sms4j 渠道（工厂取内置映射）。 */
    public static SmsChannelProvider of(String channelCode) {
        return new SmsChannelProvider(channelCode, null);
    }

    /** 声明一个渠道并显式指定 sms4j Factory 全类名（内置清单外的厂商）。 */
    public static SmsChannelProvider of(String channelCode, String factoryClassName) {
        return new SmsChannelProvider(channelCode, factoryClassName);
    }

    /** 渠道标识（sms4j supplier，如 alibaba / tencent / huawei）。 */
    public String channelCode() {
        return channelCode;
    }

    /** sms4j Factory 全类名：显式指定优先，否则取内置映射，未知返回 null。 */
    public String factoryClassName() {
        return factoryClassName != null ? factoryClassName
                : SmsSupplierFactories.factoryClassName(channelCode);
    }

    @Override
    public String toString() {
        return "SmsChannelProvider{channelCode='" + channelCode + "'}";
    }
}
