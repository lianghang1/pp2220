package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 企微群机器人-文件消息：原生 sendFile(webhookUrl, mediaId) 收 String，自定义 bean + 拆参。 */
public class WeComRobotFileMsg implements NotificationMessage {
    private String webhookKey;
    private String mediaId;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotFileMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }
    public String getMediaId() { return mediaId; }
    public WeComRobotFileMsg setMediaId(String mediaId) { this.mediaId = mediaId; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
