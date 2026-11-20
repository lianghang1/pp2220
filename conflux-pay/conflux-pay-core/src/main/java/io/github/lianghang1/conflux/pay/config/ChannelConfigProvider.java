package io.github.lianghang1.conflux.pay.config;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;

/**
 * 渠道配置提供者 SPI：按配置 id + 渠道换取渠道配置。
 *
 * <p>starter 提供基于配置文件（{@code conflux.pay.configs.<configId>.<channelCode>.*}）
 * 的默认实现；使用方也可自行实现（如从自己的数据库/配置中心读取），
 * 支持普通商户与服务商（ISV）两种模式的参数组织。</p>
 *
 * <p>实现要求：线程安全；每次读取（支持配置变更重建）；查不到配置时抛
 * {@code GatewayException(CONFIG_MISSING)}；返回对象须为该渠道自定义的
 * {@link io.github.lianghang1.conflux.pay.channel.ChannelConfig} 子类。</p>
 *
 * <p>正向调用与回调统一走本接口：使用方按 configId + channelCode 取配置，
 * 框架不再从回调报文里隐式反查账户键。</p>
 *
 * @author lianghang
 */
public interface ChannelConfigProvider {

    /** 按 configId + 渠道编码换取配置（下单/退款/转账/分账/对账/回调验签全链路）。 */
    ChannelConfig getConfig(String configId, String channelCode);
}
