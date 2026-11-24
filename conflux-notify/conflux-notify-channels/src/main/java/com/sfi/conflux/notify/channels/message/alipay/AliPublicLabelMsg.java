package com.sfi.conflux.notify.channels.message.alipay;

import com.alipay.api.domain.AlipayOpenPublicMessageLabelSendModel;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 支付宝生活号-按标签群发：继承原生 LabelSendModel。 */
public class AliPublicLabelMsg extends AlipayOpenPublicMessageLabelSendModel implements NotificationMessage {
    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_PUBLIC; }
}
