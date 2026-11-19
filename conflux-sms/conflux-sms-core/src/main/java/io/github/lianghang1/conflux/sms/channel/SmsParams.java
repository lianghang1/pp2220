package io.github.lianghang1.conflux.sms.channel;

/**
 * 短信渠道参数契约：渠道包定义自己的参数类型实现本接口，
 * 承载该渠道的商户凭证（accessKey/secret/签名/模板等）。
 *
 * <p>框架对参数内容零感知——渠道包在自己的 Handler 内向下转型。
 * 这与支付域 {@code ChannelParams} / 发票域 {@code InvoiceChannelParams} 完全同构。</p>
 *
 * @author lianghang
 */
public interface SmsParams {

    /**
     * 渠道侧应用标识：作为该配置的唯一身份（如阿里云 accessKeyId），
     * 用于配置装载期冲突检测。无对应概念时可返回 null。
     */
    String channelAppId();
}
