package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.cp.bean.article.NewArticle;

import java.util.ArrayList;

/** 企微群机器人-图文消息：原生 sendNews(webhookUrl, List&lt;NewArticle&gt;) 收可继承的 NewArticle，本类即该 List。 */
public class WeComRobotNewsMsg extends ArrayList<NewArticle> implements NotificationMessage {
    private String webhookKey;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotNewsMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
