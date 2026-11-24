package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.WxMpMassOpenIdsMessage;

/**
 * 公众号 OpenID 群发：继承原生 {@link WxMpMassOpenIdsMessage}，交给 {@code WxMpMassMessageService.massOpenIdsMessageSend}。
 *
 * @author lianghang
 */
public class MpMassOpenIdsMsg extends WxMpMassOpenIdsMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
