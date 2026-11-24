package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;

/**
 * 公众号模板消息：继承原生 {@link WxMpTemplateMessage}，交给 {@code WxMpTemplateMsgService.sendTemplateMsg}。
 *
 * @author lianghang
 */
public class MpTemplateMsg extends WxMpTemplateMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
