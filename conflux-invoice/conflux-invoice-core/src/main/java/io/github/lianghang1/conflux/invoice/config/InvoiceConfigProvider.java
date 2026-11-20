package io.github.lianghang1.conflux.invoice.config;

import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;

import java.util.List;

/**
 * 发票渠道配置提供者 SPI。
 *
 * <p>相较支付域的二维（configId + channelCode），这里需要三维定位：
 * configId + channelCode + sellerTaxNo（企业准入标识）。同时提供
 * 「可路由实例列表」（通道分级路由用）。</p>
 *
 * <p>正向调用与回调统一走本接口：使用方按 configId + channelCode + sellerTaxNo
 * 取配置，框架不再从回调报文里隐式反查账户键。</p>
 *
 * <p>实现要求：线程安全；每次读取；查不到配置抛 {@code CONFIG_MISSING}。</p>
 *
 * @author lianghang
 */
public interface InvoiceConfigProvider {

    /** 按 configId + 渠道编码 + 销方税号换取配置（正向调用与回调验签全链路）。 */
    ChannelConfig getConfig(String configId, String channelCode, String sellerTaxNo);

    /** 该配置 + 销方税号下所有可用渠道实例（通道分级路由用）。 */
    List<ChannelInstance> instances(String configId, String sellerTaxNo);
}
