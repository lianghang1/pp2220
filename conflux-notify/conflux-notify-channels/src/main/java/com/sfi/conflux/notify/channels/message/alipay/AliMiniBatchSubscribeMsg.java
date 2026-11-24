package com.sfi.conflux.notify.channels.message.alipay;

import com.alipay.api.domain.AlipayOpenAppMiniTemplatemessageBatchsendModel;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 支付宝小程序-订阅消息（批量/人群）：继承原生 BatchsendModel。 */
public class AliMiniBatchSubscribeMsg extends AlipayOpenAppMiniTemplatemessageBatchsendModel implements NotificationMessage {
    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_MINI; }
}
