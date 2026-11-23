package com.sfi.conflux.file.config;

import java.util.Collections;
import java.util.List;

/**
 * 文件渠道配置提供者 SPI：按应用与渠道编码换取配置。
 *
 * <p>与支付域 {@code ChannelConfigProvider}、发票域 {@code InvoiceConfigProvider}、
 * 通知域 {@code NotificationConfigProvider} 完全同构：starter 提供基于配置文件的默认实现；
 * 使用方也可自行实现（如从数据库/配置中心读取），每次调用都重新读取，配置变更
 * 可被装载层感知并重建 {@code FileStorageService}。</p>
 *
 * <p>实现要求：线程安全；查不到配置时抛 {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface FileConfigProvider {

    /** 按 configId + 渠道编码换取配置（业务链路）。 */
    FileChannelConfig getConfig(String configId, String channelCode);

    /**
     * 枚举全部已配置平台（启动期预热装载 / fail-fast 用）；默认空。
     * 自定义 provider（DB/配置中心）可覆盖；不影响 {@link #getConfig} 主契约。
     */
    default List<FileChannelConfig> allConfigs() {
        return Collections.emptyList();
    }
}
