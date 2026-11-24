package com.sfi.conflux.notify.channels.message.mp;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.mp.bean.WxMpMassPreviewMessage;

/**
 * 公众号群发预览：继承原生 {@link WxMpMassPreviewMessage}，交给 {@code WxMpMassMessageService.massMessagePreview}。
 *
 * @author lianghang
 */
public class MpMassPreviewMsg extends WxMpMassPreviewMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }
}
