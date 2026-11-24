package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.cp.bean.message.WxCpGroupRobotMessage;

/** 企微群机器人-模板卡片消息：继承原生 {@link WxCpGroupRobotMessage}（有全套 setter），原生 sendTemplateCardMessage 直接接收。 */
public class WeComRobotCardMsg extends WxCpGroupRobotMessage implements NotificationMessage {
    private String webhookKey;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotCardMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
