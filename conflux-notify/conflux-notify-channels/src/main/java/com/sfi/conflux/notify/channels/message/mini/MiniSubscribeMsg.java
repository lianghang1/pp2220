package com.sfi.conflux.notify.channels.message.mini;

import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/**
 * 微信小程序订阅消息：继承 wxjava 原生 {@link WxMaSubscribeMessage}，字段用原生 setter 填充，
 * 直接交给 {@code WxMaMsgService.sendSubscribeMsg}。
 *
 * @author lianghang
 */
public class MiniSubscribeMsg extends WxMaSubscribeMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MINI;
    }
}
