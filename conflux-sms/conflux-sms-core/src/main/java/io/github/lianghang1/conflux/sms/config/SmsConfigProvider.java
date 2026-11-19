package io.github.lianghang1.conflux.sms.config;

import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;

/**
 * 渠道配置提供者 SPI：按 appId + 渠道换取配置。
 *
 * <p>starter 提供基于配置文件的默认实现（{@code conflux.sms.apps.<appId>.<channel>}）；
 * 使用方也可自行实现（如从数据库/配置中心读取）。查不到配置时抛
 * {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface SmsConfigProvider {

    /** 按 appId + 渠道编码换取配置。 */
    SmsChannelConfig getConfig(String appId, String channelCode);
}
