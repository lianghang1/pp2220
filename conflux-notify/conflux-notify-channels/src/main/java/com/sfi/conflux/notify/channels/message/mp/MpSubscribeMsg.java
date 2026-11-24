package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.subscribe.WxMpSubscribeMessage;

/**
 * 公众号订阅消息：继承原生 {@link WxMpSubscribeMessage}，交给 {@code WxMpSubscribeMsgService.send}。
 *
 * @author lianghang
 */
public class MpSubscribeMsg extends WxMpSubscribeMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
