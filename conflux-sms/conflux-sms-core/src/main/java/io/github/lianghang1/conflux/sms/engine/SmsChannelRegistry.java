package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channel.SmsSender;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信渠道注册表：渠道编码 → 提供者，及发送能力查找入口。
 *
 * <p>注册发生在网关组装阶段（启动期），运行期只读；线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class SmsChannelRegistry {

    private final Map<String, SmsChannelProvider> providers =
            new ConcurrentHashMap<String, SmsChannelProvider>();

    /** 注册渠道提供者；编码重复时保留先注册者。 */
    public void register(SmsChannelProvider provider) {
        String code = provider.channelCode();
        SmsChannelProvider existing = providers.putIfAbsent(code, provider);
        if (existing != null) {
            log.warn("短信渠道提供者重复注册，保留先注册者: channel={}", code);
            return;
        }
        log.info("短信渠道已注册: channel={}", code);
    }

    /** 按编码取提供者；未注册抛明确异常。 */
    public SmsChannelProvider provider(String channelCode) {
        SmsChannelProvider provider = channelCode == null ? null : providers.get(channelCode);
        if (provider == null) {
            throw new GatewayException(SmsErrorCode.CHANNEL_NOT_FOUND, "短信渠道未注册: {}", channelCode);
        }
        return provider;
    }

    /** 按编码取发送能力；不支持抛明确异常。 */
    public SmsSender sender(String channelCode) {
        SmsSender sender = provider(channelCode).smsSender();
        if (sender == null) {
            throw new GatewayException(SmsErrorCode.CAPABILITY_NOT_SUPPORTED,
                    "短信渠道[{}]不支持发送能力", channelCode);
        }
        return sender;
    }

    /** 已注册渠道编码集合。 */
    public Set<String> registeredChannels() {
        return providers.keySet();
    }
}
