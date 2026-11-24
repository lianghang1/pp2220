package com.sfi.conflux.notify.channel;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.config.NotificationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 渠道模板基类（借鉴登录域 {@code AbstractAuthChannel}）：固化上下文回填与异常语义，
 * provider 只需实现 {@link #doSend} 承载渠道脏活。
 *
 * <p>{@link #send} 为 final，provider 无法覆盖框架流程：</p>
 * <ul>
 *   <li>配置/装载类异常（{@link GatewayException}，如配置缺失、渠道未注册）<b>原样透传</b> fail-fast，
 *       与门面及支付/发票「坏配置即失败」语义一致；</li>
 *   <li>渠道调用阶段的其他异常归一为失败结果（不向外抛），避免单个渠道故障击穿调用方；</li>
 *   <li>成功/失败结果统一回填 channel / configId，短信请求额外回填 bizId，provider 只需关心 success/raw/error。</li>
 * </ul>
 *
 * @author lianghang
 */
public abstract class AbstractNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(AbstractNotificationChannel.class);

    @Override
    public final NotificationSendResult send(NotificationConfig config, NotificationMessage message) {
        NotificationSendResult result;
        try {
            result = doSend(config, message);
        } catch (GatewayException e) {
            // 配置/装载错误 fail-fast，保持与门面及其他域一致的异常语义
            throw e;
        } catch (Exception e) {
            log.warn("通知渠道调用失败, channelCode={}", channelCode(), e);
            result = new NotificationSendResult().setSuccess(false).setError(e.getMessage());
        }
        if (result == null) {
            result = new NotificationSendResult();
        }
        fillContext(result, config, message);
        return result;
    }

    /** 回填统一上下文字段。 */
    private void fillContext(NotificationSendResult result, NotificationConfig config, NotificationMessage message) {
        // bizId 是短信请求字段：保持 NotificationMessage 接口只有 channelCode，按类型回填
        if (message instanceof NotificationSendRequest && result.getBizId() == null) {
            result.setBizId(((NotificationSendRequest) message).getBizId());
        }
        result.setChannel(channelCode());
        if (config != null) {
            result.setConfigId(config.getConfigId());
        }
    }

    /**
     * 渠道实现：完成真正的配置装载 + SDK 调用 + 成功/失败归一。
     * 允许抛受检异常（如 wxjava 的 WxErrorException），由 {@link #send} 统一归一为失败结果。
     */
    protected abstract NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception;
}
