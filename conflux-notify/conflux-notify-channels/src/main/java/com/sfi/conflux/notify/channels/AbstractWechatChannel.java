package com.sfi.conflux.notify.channels;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.AbstractNotificationChannel;
import com.sfi.conflux.notify.channels.support.WxCredentialMapper;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 微信系通知渠道公共基类：复用通知域渠道模板（异常归一 + 上下文回填），并提供凭证映射入口。
 *
 * <p>各平台渠道只需实现 channelCode 与按 bean 类型分发的 doSend；SDK 调用异常由基类归一为失败结果。</p>
 *
 * @author lianghang
 */
public abstract class AbstractWechatChannel extends AbstractNotificationChannel {

    /** 小程序/公众号凭证映射（attr.appId + attr.secret）。 */
    protected final PlatformCredential credential(NotificationConfig config) {
        return WxCredentialMapper.appIdSecret(config);
    }

    /** 企业微信凭证映射（attr.corpId + attr.corpSecret，agentId 透传）。 */
    protected final PlatformCredential corpCredential(NotificationConfig config) {
        return WxCredentialMapper.corp(config);
    }
}
