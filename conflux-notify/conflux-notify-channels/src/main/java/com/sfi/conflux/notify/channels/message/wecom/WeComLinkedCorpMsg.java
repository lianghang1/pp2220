package com.sfi.conflux.notify.channels.message.wecom;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import me.chanjar.weixin.cp.bean.message.WxCpLinkedCorpMessage;

/**
 * 企微互联企业消息：继承原生 {@link WxCpLinkedCorpMessage}，交给 {@code WxCpMessageService.sendLinkedCorpMessage}。
 *
 * @author lianghang
 */
public class WeComLinkedCorpMsg extends WxCpLinkedCorpMessage implements NotificationMessage {

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_WECOM;
    }
}
