package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 企微群机器人-Markdown 消息：原生 sendMarkdown 收 String，自定义 bean + 拆参。 */
public class WeComRobotMarkdownMsg implements NotificationMessage {
    private String webhookKey;
    private String content;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotMarkdownMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }
    public String getContent() { return content; }
    public WeComRobotMarkdownMsg setContent(String content) { this.content = content; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
