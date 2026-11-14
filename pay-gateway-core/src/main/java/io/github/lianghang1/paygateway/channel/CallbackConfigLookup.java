package io.github.lianghang1.paygateway.channel;

/**
 * 回调期配置换算：按渠道账户键换取网关渠道配置。
 *
 * <p>由引擎基于 {@code ChannelConfigProvider} 实现；渠道包在解析回调时
 * 通过 {@link ChannelAccountKey#resolve} 取出账户键后调用本接口。
 * 查不到配置时实现抛 {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface CallbackConfigLookup {

    /** 按渠道账户键换取网关渠道配置。 */
    ChannelConfig byAccountKey(String accountKey);
}
