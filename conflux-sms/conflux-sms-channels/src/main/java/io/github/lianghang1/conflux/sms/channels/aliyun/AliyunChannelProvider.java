package io.github.lianghang1.conflux.sms.channels.aliyun;

import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channel.SmsParams;
import io.github.lianghang1.conflux.sms.channel.SmsSender;

import java.util.Map;

/**
 * 阿里云短信渠道提供者（薄门面，底层 sms4j）。
 *
 * <p>渠道编码 {@code aliyun}。与支付域 AlipayChannelProvider 同构：
 * parseParams 把 attrs 解析为渠道参数对象，smsSender 暴露发送能力。</p>
 *
 * @author lianghang
 */
public class AliyunChannelProvider implements SmsChannelProvider {

    public static final String CHANNEL_CODE = "aliyun";

    private final SmsSender sender = new AliyunSender();

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public SmsParams parseParams(Map<String, String> properties) {
        return AliyunParams.fromMap(properties);
    }

    @Override
    public SmsSender smsSender() {
        return sender;
    }
}
