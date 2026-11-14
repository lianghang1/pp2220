package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.api.CallbackEvent;
import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;

/**
 * 渠道回调处理器：验签、解析、生成渠道应答报文。
 *
 * <p>实现约定：验签失败抛 {@code GatewayException(SIGN_INVALID)}、
 * 报文不合法抛 {@code GatewayException(CALLBACK_PARSE_FAILED)}，由使用方决定
 * 以 {@link #failureReply()} 应答渠道触发其重推；解析成功返回归一化
 * {@link CallbackEvent}，后续状态迁移由引擎完成。实现必须线程安全。</p>
 *
 * @author lianghang
 */
public interface ChannelCallbackHandler {

    /**
     * 验签并解析回调报文。
     *
     * <p>渠道侧应用标识（如微信 appid）先从报文提取，再经
     * {@link CallbackConfigLookup} 换取配置用于验签——回调到达时引擎尚不知道
     * appId，配置换算是解析过程的一部分。</p>
     */
    CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup);

    /** 渠道认可的「处理成功」应答报文（原样写回渠道）。 */
    String successReply();

    /** 渠道认可的「处理失败」应答报文（触发渠道重推）。 */
    String failureReply();
}
