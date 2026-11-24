package com.sfi.conflux.notify.channels.message.mini;

import cn.binarywang.wx.miniapp.bean.WxMaKefuMessage;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/**
 * 微信小程序客服消息：继承 wxjava 原生 {@link WxMaKefuMessage}，交给 {@code WxMaMsgService.sendKefuMsg}。
 *
 * @author lianghang
 */
public class MiniKefuMsg extends WxMaKefuMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MINI;
    }
}
