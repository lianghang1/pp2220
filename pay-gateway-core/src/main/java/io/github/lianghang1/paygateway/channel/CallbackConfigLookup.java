package io.github.lianghang1.paygateway.channel;

/**
 * 回调期配置换算：按渠道侧应用标识换取网关渠道配置。
 *
 * <p>由引擎基于 {@code ChannelConfigProvider} 实现；渠道包在解析回调时
 * 从报文提取渠道侧应用标识后调用本接口。查不到配置时实现抛
 * {@code GatewayException(CONFIG_MISSING)}。</p>
 *
 * @author lianghang
 */
public interface CallbackConfigLookup {

    /** 按渠道侧应用标识（如微信 appid）换取网关渠道配置。 */
    ChannelConfig byChannelApp(String channelAppId);
}
