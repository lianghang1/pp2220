package com.sfi.conflux.notify.channels.message.mini;

import cn.binarywang.wx.miniapp.bean.WxMaUniformMessage;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/**
 * 微信小程序统一消息：继承 wxjava 原生 {@link WxMaUniformMessage}，交给 {@code WxMaMsgService.sendUniformMsg}。
 *
 * @author lianghang
 */
public class MiniUniformMsg extends WxMaUniformMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MINI;
    }
}
