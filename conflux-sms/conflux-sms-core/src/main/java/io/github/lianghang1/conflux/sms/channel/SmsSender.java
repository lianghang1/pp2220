package io.github.lianghang1.conflux.sms.channel;

import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;

/**
 * 渠道发送能力：短信域的核心能力（发送即终态，无状态机/回调状态推进）。
 *
 * <p>实现线程安全；渠道调用失败抛 {@code GatewayException(CHANNEL_CALL_FAILED)}；
 * 本接口刻意保持最小——PoC 阶段只暴露发送，查单/回执等按真实需求再扩展。</p>
 *
 * @author lianghang
 */
public interface SmsSender {

    /** 发送短信，返回归一结果。 */
    SmsSendResult send(SmsSendRequest request, SmsChannelConfig config);
}
