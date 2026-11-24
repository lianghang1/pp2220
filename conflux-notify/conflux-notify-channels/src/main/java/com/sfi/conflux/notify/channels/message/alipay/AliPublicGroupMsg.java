package com.sfi.conflux.notify.channels.message.alipay;

import com.alipay.api.domain.AlipayOpenPublicMessageGroupSendModel;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 支付宝生活号-按分组群发：继承原生 GroupSendModel。 */
public class AliPublicGroupMsg extends AlipayOpenPublicMessageGroupSendModel implements NotificationMessage {
    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_PUBLIC; }
}
