package com.sfi.conflux.notify.channels.mp;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractWechatChannel;
import com.sfi.conflux.notify.channels.message.mp.MpKefuMsg;
import com.sfi.conflux.notify.channels.message.mp.MpMassOpenIdsMsg;
import com.sfi.conflux.notify.channels.message.mp.MpMassPreviewMsg;
import com.sfi.conflux.notify.channels.message.mp.MpMassTagMsg;
import com.sfi.conflux.notify.channels.message.mp.MpSubscribeMsg;
import com.sfi.conflux.notify.channels.message.mp.MpTemplateMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.wechat.WechatClients;
import me.chanjar.weixin.mp.api.WxMpService;

/**
 * 微信公众号消息渠道：一份公众号凭证覆盖模板/客服/群发/订阅全部消息类型，按 bean 类型分发。
 *
 * @author lianghang
 */
public class WechatMpChannel extends AbstractWechatChannel {

    private final WechatClients clients;
    /** 测试注入用：直接给定原生 service，跳过凭证映射与平台层，便于 mock。 */
    private final WxMpService injectedService;

    public WechatMpChannel(WechatClients clients) {
        this.clients = clients;
        this.injectedService = null;
    }

    /** 供单测注入 mock service（生产不走此构造器）。 */
    public WechatMpChannel(WxMpService injectedService) {
        this.clients = null;
        this.injectedService = injectedService;
    }

    public WechatMpChannel() {
        this(new WechatClients());
    }

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MP;
    }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        WxMpService service = injectedService != null ? injectedService : clients.mp(credential(config));
        NotificationSendResult result = new NotificationSendResult();
        if (message instanceof MpTemplateMsg) {
            result.setRaw(service.getTemplateMsgService().sendTemplateMsg((MpTemplateMsg) message));
        } else if (message instanceof MpKefuMsg) {
            result.setRaw(service.getKefuService().sendKefuMessage((MpKefuMsg) message));
        } else if (message instanceof MpSubscribeMsg) {
            result.setRaw(service.getSubscribeMsgService().send((MpSubscribeMsg) message));
        } else if (message instanceof MpMassTagMsg) {
            result.setRaw(service.getMassMessageService().massGroupMessageSend((MpMassTagMsg) message));
        } else if (message instanceof MpMassOpenIdsMsg) {
            result.setRaw(service.getMassMessageService().massOpenIdsMessageSend((MpMassOpenIdsMsg) message));
        } else if (message instanceof MpMassPreviewMsg) {
            result.setRaw(service.getMassMessageService().massMessagePreview((MpMassPreviewMsg) message));
        } else {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "公众号渠道不支持的消息类型: {}", message.getClass().getName());
        }
        return result.setSuccess(true);
    }
}
