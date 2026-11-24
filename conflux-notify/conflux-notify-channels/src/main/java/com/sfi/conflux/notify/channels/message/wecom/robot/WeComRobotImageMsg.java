package com.sfi.conflux.notify.channels.message.wecom.robot;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 企微群机器人-图片消息：原生 sendImage(webhookUrl, base64, md5) 收 String，自定义 bean + 拆参。 */
public class WeComRobotImageMsg implements NotificationMessage {
    private String webhookKey;
    private String base64;
    private String md5;

    public String getWebhookKey() { return webhookKey; }
    public WeComRobotImageMsg setWebhookKey(String webhookKey) { this.webhookKey = webhookKey; return this; }
    public String getBase64() { return base64; }
    public WeComRobotImageMsg setBase64(String base64) { this.base64 = base64; return this; }
    public String getMd5() { return md5; }
    public WeComRobotImageMsg setMd5(String md5) { this.md5 = md5; return this; }

    @Override
    public String channelCode() { return NotifyChannels.WECHAT_WECOM; }
}
