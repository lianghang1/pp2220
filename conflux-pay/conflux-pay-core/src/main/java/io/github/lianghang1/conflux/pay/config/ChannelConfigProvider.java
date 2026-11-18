package io.github.lianghang1.conflux.pay.config;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;

/**
 * 渠道配置提供者 SPI：按应用与渠道换取渠道配置。
 *
 * <p>starter 提供基于配置文件（{@code conflux.pay.apps.<appId>.<channel>.*}）
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
     * 按渠道账户键换取配置（渠道回调验签链路）。
     * 账户键由 {@link io.github.lianghang1.conflux.pay.channel.ChannelAccountKey#of(String, String)}
     * 生成：普通商户即 {@link ChannelParams#channelAppId()} 的取值（如微信 appid），
     * 服务商模式为其后追加 {@code #subMchNo} 的复合键。
     *
     * <p>实现方负责「账户键 → 配置」的映射，且必须保证该映射在装载期唯一：
     * 出现两个配置生成相同账户键时应立即失败（{@code CONFIG_CONFLICT}），
     * 不得静默覆盖，否则回调会归到错误的 appId 或用错验签密钥。</p>
     */
    ChannelConfig getByAccountKey(String channelCode, String accountKey);
}
