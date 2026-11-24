package com.sfi.conflux.notify.channels.message.wecom;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.cp.bean.message.WxCpAppChatMessage;

/**
 * 企微群聊消息：继承原生 {@link WxCpAppChatMessage}，交给 {@code WxCpChatService.sendMsg}。
 *
 * @author lianghang
 */
public class WeComAppChatMsg extends WxCpAppChatMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_WECOM;
    }
}
