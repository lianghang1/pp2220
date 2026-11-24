package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.WxMpMassTagMessage;

/**
 * 公众号标签群发：继承原生 {@link WxMpMassTagMessage}，交给 {@code WxMpMassMessageService.massGroupMessageSend}。
 *
 * @author lianghang
 */
public class MpMassTagMsg extends WxMpMassTagMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
