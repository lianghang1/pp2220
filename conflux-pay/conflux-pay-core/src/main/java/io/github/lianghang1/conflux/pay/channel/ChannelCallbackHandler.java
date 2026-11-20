package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;

/**
 * 渠道回调处理器：验签、解析、生成渠道应答报文。
 *
 * <p>实现约定：验签失败抛 {@code GatewayException(SIGN_INVALID)}、
 * 报文不合法抛 {@code GatewayException(CALLBACK_PARSE_FAILED)}，由使用方决定
 * 以 {@link #failureReply()} 应答渠道触发其重推；解析成功返回归一化
 * {@link CallbackEvent}。实现必须线程安全。</p>
 *
 * <p>配置由使用方按 configId 查库后经门面传入（{@code parseCallback(configId, raw)}），
 * 框架据此取到 config 直接验签——不再从回调报文里隐式反查账户键。</p>
 *
 * @author lianghang
 */
public interface ChannelCallbackHandler {

    /**
     * 验签并解析回调报文。
     *
     * @param raw    渠道回调原始请求
     * @param config 使用方按 configId + channelCode 定位的渠道配置，用于验签/解密
     */
    CallbackEvent parseCallback(ChannelCallbackRequest raw, ChannelConfig config);

    /** 渠道认可的「处理成功」应答报文（原样写回渠道）。 */
    String successReply();

    /** 渠道认可的「处理失败」应答报文（触发渠道重推）。 */
    String failureReply();
}
