package com.sfi.conflux.notify.channels.message.alipay;

import com.alipay.api.domain.AlipayOpenAppMiniTemplatemessageSendModel;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

/**
 * 支付宝小程序-订阅消息（单发）：继承原生 {@link AlipayOpenAppMiniTemplatemessageSendModel}，
 * 渠道内 {@code request.setBizModel(this)} 后交 {@code AlipayClient.execute}。
 *
 * @author lianghang
 */
public class AliMiniSubscribeMsg extends AlipayOpenAppMiniTemplatemessageSendModel implements NotificationMessage {
    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_MINI; }
}
