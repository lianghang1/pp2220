package com.sfi.conflux.sms.config;

import java.util.Collections;
import java.util.List;

/**
 * 短信配置提供者 SPI：按 configId + 渠道换取自有配置对象 {@link SmsChannelConfig}。
 *
 * <p>与支付域 {@code ChannelConfigProvider}、发票域 {@code InvoiceConfigProvider}
 * 完全同构：starter 提供基于配置文件（{@code conflux.sms.configs.<configId>.<channelCode>}）
 * 的默认实现；使用方也可自行实现（如从自己的数据库/配置中心读取），
 * 每次调用都重新读取，凭证变更可被装载层感知并重建客户端。</p>
 *
 * <p>实现要求：线程安全；查不到配置时抛
 * {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface SmsConfigProvider {

    /** 按 configId + 渠道编码换取配置（发送链路）。 */
    SmsChannelConfig getConfig(String configId, String channelCode);

    /**
     * 枚举全部已配置渠道（启动期预热装载 / fail-fast 用）；默认空。
     * 自定义 provider（DB/配置中心）可覆盖；不影响 {@link #getConfig} 主契约。
     */
    default List<SmsChannelConfig> allConfigs() {
        return Collections.emptyList();
    }
}
