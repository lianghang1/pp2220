package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;

/**
 * 公众号客服消息：继承原生 {@link WxMpKefuMessage}，交给 {@code WxMpKefuService.sendKefuMessage}。
 *
 * @author lianghang
 */
public class MpKefuMsg extends WxMpKefuMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
