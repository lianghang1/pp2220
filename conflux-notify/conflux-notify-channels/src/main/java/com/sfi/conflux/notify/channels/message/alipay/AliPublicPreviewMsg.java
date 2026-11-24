package com.sfi.conflux.notify.channels.message.alipay;

import com.alipay.api.domain.AlipayOpenPublicMessagePreviewSendModel;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/** 支付宝生活号-预览消息：继承原生 PreviewSendModel。 */
public class AliPublicPreviewMsg extends AlipayOpenPublicMessagePreviewSendModel implements NotificationMessage {
    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_PUBLIC; }
}
