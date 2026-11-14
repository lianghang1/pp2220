package io.github.lianghang1.paygateway.config;

import io.github.lianghang1.paygateway.channel.ChannelConfig;

/**
 * 渠道配置提供者 SPI：按应用与渠道换取渠道配置。
 *
 * <p>starter 提供基于配置文件（{@code pay-gateway.apps.<appId>.channels.<channel>.*}）
 * 的默认实现；使用方也可自行实现（如从自己的数据库/配置中心读取），
 * 支持普通商户与服务商（ISV）两种模式的参数组织。</p>
 *
 * <p>实现要求：线程安全；查不到配置时抛
 * {@code GatewayException(CONFIG_MISSING)}；返回对象的 params 字段须为
 * 该渠道自定义的 {@code ChannelParams} 实现。</p>
 *
 * @author lianghang
 */
public interface ChannelConfigProvider {

    /** 按 appId + 渠道编码换取配置（下单/退款/转账/分账/对账链路）。 */
    ChannelConfig getConfig(String appId, String channelCode);

    /**
     * 按渠道侧应用标识换取配置（渠道回调验签链路）。
     * 渠道侧标识即 {@link ChannelParams#channelAppId()} 的取值（如微信 appid）；
     * 实现方负责「渠道侧标识 → 配置」的映射（默认实现可维护反向索引）。
     */
    ChannelConfig getByChannelApp(String channelCode, String channelAppId);
}
