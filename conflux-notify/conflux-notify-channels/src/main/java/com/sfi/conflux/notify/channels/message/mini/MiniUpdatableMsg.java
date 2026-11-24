package com.sfi.conflux.notify.channels.message.mini;

import cn.binarywang.wx.miniapp.bean.WxMaUpdatableMsg;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/**
 * 微信小程序动态消息：继承 wxjava 原生 {@link WxMaUpdatableMsg}，交给 {@code WxMaMsgService.setUpdatableMsg}。
 *
 * @author lianghang
 */
public class MiniUpdatableMsg extends WxMaUpdatableMsg implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MINI;
    }
}
