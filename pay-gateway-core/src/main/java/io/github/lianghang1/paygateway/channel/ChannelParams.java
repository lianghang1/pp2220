package io.github.lianghang1.paygateway.channel;

/**
 * 渠道参数契约：各渠道包定义自己的参数类型实现本接口，
 * 承载该渠道的商户/应用凭证（密钥、证书、网关地址等）。
 *
 * <p>框架对参数内容零感知——渠道包在自己的 Handler 内向下转型为自己定义的类型；
 * 类型不匹配时按 {@code CONFIG_MISSING} 语义报错，快速暴露配置错装问题。</p>
 *
 * <p>{@link #channelAppId()} 同时是回调配置反查账户键的输入：框架会结合
 * {@code ChannelConfig.subMchNo} 经 {@link ChannelAccountKey#of(String, String)}
 * 生成唯一账户键。多个配置生成相同账户键时配置装载直接失败，不再静默覆盖。</p>
 *
 * @author lianghang
 */
public interface ChannelParams {

    /**
     * 渠道侧应用标识：该配置在渠道侧的唯一身份（如微信 appid、支付宝网关应用、
     * 间联渠道的商户号），用于渠道回调报文的配置反查；无对应概念的渠道返回商户号等
     * 等效标识，确实没有时返回 null（此类渠道的回调需使用方自行路由）。
     */
    String channelAppId();
}
