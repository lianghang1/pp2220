package com.sfi.conflux.notify.channels.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayOpenAppMiniTemplatemessageBatchsendRequest;
import com.alipay.api.request.AlipayOpenAppMiniTemplatemessageSendRequest;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractAlipayChannel;
import com.sfi.conflux.notify.channels.message.alipay.AliMiniBatchSubscribeMsg;
import com.sfi.conflux.notify.channels.message.alipay.AliMiniSubscribeMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.platform.alipay.AlipayClients;

/**
 * 支付宝小程序通知渠道：一份小程序凭证覆盖订阅消息（单发/批量），按 bean 类型分发。
 *
 * @author lianghang
 */
public class AlipayMiniChannel extends AbstractAlipayChannel {

    public AlipayMiniChannel(AlipayClients clients) { super(clients); }

    /** 供单测注入 mock client（生产不走此构造器）。 */
    public AlipayMiniChannel(AlipayClient injectedClient) { super(injectedClient); }

    public AlipayMiniChannel() { this(new AlipayClients()); }

    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_MINI; }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        if (message instanceof AliMiniSubscribeMsg) {
            return result(exec(config, new AlipayOpenAppMiniTemplatemessageSendRequest(), (AliMiniSubscribeMsg) message));
        } else if (message instanceof AliMiniBatchSubscribeMsg) {
            return result(exec(config, new AlipayOpenAppMiniTemplatemessageBatchsendRequest(), (AliMiniBatchSubscribeMsg) message));
        }
        throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                "支付宝小程序渠道不支持的消息类型: {}", message.getClass().getName());
    }
}
