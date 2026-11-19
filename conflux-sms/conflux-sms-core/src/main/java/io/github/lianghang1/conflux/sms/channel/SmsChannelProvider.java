package io.github.lianghang1.conflux.sms.channel;

import java.util.Map;

/**
 * 短信渠道提供者 SPI：一个渠道实现的统一入口。
 *
 * <p>与支付域 ChannelProvider / 发票域 InvoiceChannelProvider 同构：
 * 声明渠道编码、把 attrs 键值对解析为渠道参数对象、暴露发送能力。
 * 渠道专属 SDK 依赖只出现在渠道包内部。</p>
 *
 * @author lianghang
 */
public interface SmsChannelProvider {

    /** 渠道编码（全局唯一，如 "aliyun"）。 */
    String channelCode();

    /**
     * 将配置 attrs 键值对解析为渠道参数对象。
     * 键名约定由渠道包自行定义并在文档中说明；不支持声明式配置时返回 null。
     */
    SmsParams parseParams(Map<String, String> properties);

    /** 发送能力；不支持返回 null。 */
    default SmsSender smsSender() {
        return null;
    }
}
