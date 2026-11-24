package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

import java.util.List;

/**
 * 企微群机器人-文本消息。原生 sendText 收散装 String 参数（无 bean 可继承），
 * 故自定义 bean 承载字段，渠道内拆参传给 SDK。
 *
 * @author lianghang
 */
public class WeComRobotTextMsg implements NotificationMessage {
    private String webhookKey;
    private String content;
    private List<String> mentionedList;
    private List<String> mentionedMobileList;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotTextMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }
    public String getContent() { return content; }
    public WeComRobotTextMsg setContent(String content) { this.content = content; return this; }
    public List<String> getMentionedList() { return mentionedList; }
    public WeComRobotTextMsg setMentionedList(List<String> mentionedList) { this.mentionedList = mentionedList; return this; }
    public List<String> getMentionedMobileList() { return mentionedMobileList; }
    public WeComRobotTextMsg setMentionedMobileList(List<String> mentionedMobileList) { this.mentionedMobileList = mentionedMobileList; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
