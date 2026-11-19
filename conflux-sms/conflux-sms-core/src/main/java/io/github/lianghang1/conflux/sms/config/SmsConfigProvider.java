package io.github.lianghang1.conflux.sms.config;

/**
 * 短信渠道配置提供者 SPI：按应用与渠道换取渠道配置。
 *
 * <p>与支付域 {@code ChannelConfigProvider}、发票域 {@code InvoiceConfigProvider}
 * 完全同构：starter 提供基于配置文件（{@code conflux.sms.apps.<appId>.<channel>}）
 * 的默认实现；使用方也可自行实现（如从自己的数据库/配置中心读取），
 * 每次调用都重新读取，凭证变更可被装载层感知并重建客户端。</p>
 *
 * <p>实现要求：线程安全；查不到配置时抛
 * {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface SmsConfigProvider {

    /** 按 appId + 渠道标识换取配置（发送链路）。 */
    SmsChannelConfig getConfig(String appId, String channel);
}
