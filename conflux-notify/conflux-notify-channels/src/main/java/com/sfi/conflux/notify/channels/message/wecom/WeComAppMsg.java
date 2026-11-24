package com.sfi.conflux.notify.channels.message.wecom;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;

/**
 * 企微应用消息：继承原生 {@link WxCpMessage}，交给 {@code WxCpMessageService.send}。
 * 通过 msgType 区分文本/图片/图文/模板卡片等具体形态（原生能力，本框架不重做）。
 *
 * @author lianghang
 */
public class WeComAppMsg extends WxCpMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_WECOM;
    }
}
