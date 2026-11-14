package io.github.lianghang1.conflux.invoice.config;

import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;

import java.util.List;

/**
 * 发票渠道配置提供者 SPI。
 *
 * <p>相较支付域的二维（appId + channelCode），这里需要三维定位：
 * appId + channelCode + sellerTaxNo（企业准入标识）。同时提供
 * 「账户键 → 配置」的回调反查与「可路由实例列表」。</p>
 *
 * <p><b>实现要求</b>：线程安全；查不到配置抛 {@code CONFIG_MISSING}；
 * 账户键映射必须在装载期保证唯一，出现两个配置生成相同账户键时立即失败
 * （{@code CONFIG_CONFLICT}），不得静默覆盖，否则回调会归到错误企业。</p>
 *
 * @author lianghang
 */
public interface InvoiceConfigProvider {

    /** 按 appId + 渠道编码 + 销方税号换取配置（正向链路）。 */
    ChannelConfig getConfig(String appId, String channelCode, String sellerTaxNo);

    /** 按渠道账户键换取配置（回调验签链路）。 */
    ChannelConfig getByAccountKey(String channelCode, String accountKey);

    /** 该应用 + 销方税号下所有可用渠道实例（通道分级路由用）。 */
    List<ChannelInstance> instances(String appId, String sellerTaxNo);
}
