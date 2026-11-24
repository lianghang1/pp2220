package com.sfi.conflux.notify.channels.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayOpenPublicMessageGroupSendRequest;
import com.alipay.api.request.AlipayOpenPublicMessageLabelSendRequest;
import com.alipay.api.request.AlipayOpenPublicMessagePreviewSendRequest;
import com.alipay.api.request.AlipayOpenPublicMessageSingleSendRequest;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractAlipayChannel;
import com.sfi.conflux.notify.channels.message.alipay.AliPublicGroupMsg;
import com.sfi.conflux.notify.channels.message.alipay.AliPublicLabelMsg;
import com.sfi.conflux.notify.channels.message.alipay.AliPublicPreviewMsg;
import com.sfi.conflux.notify.channels.message.alipay.AliPublicSingleMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.alipay.AlipayClients;

/**
 * 支付宝生活号通知渠道：单发/标签群发/分组群发/预览，按 bean 类型分发。
 *
 * @author lianghang
 */
public class AlipayPublicChannel extends AbstractAlipayChannel {

    public AlipayPublicChannel(AlipayClients clients) { super(clients); }

    /** 供单测注入 mock client（生产不走此构造器）。 */
    public AlipayPublicChannel(AlipayClient injectedClient) { super(injectedClient); }

    public AlipayPublicChannel() { this(new AlipayClients()); }

    @Override
    public String channelCode() { return NotifyChannels.ALIPAY_PUBLIC; }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        if (message instanceof AliPublicSingleMsg) {
            return result(exec(config, new AlipayOpenPublicMessageSingleSendRequest(), (AliPublicSingleMsg) message));
        } else if (message instanceof AliPublicLabelMsg) {
            return result(exec(config, new AlipayOpenPublicMessageLabelSendRequest(), (AliPublicLabelMsg) message));
        } else if (message instanceof AliPublicGroupMsg) {
            return result(exec(config, new AlipayOpenPublicMessageGroupSendRequest(), (AliPublicGroupMsg) message));
        } else if (message instanceof AliPublicPreviewMsg) {
            return result(exec(config, new AlipayOpenPublicMessagePreviewSendRequest(), (AliPublicPreviewMsg) message));
        }
        throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                "支付宝生活号渠道不支持的消息类型: {}", message.getClass().getName());
    }
}
