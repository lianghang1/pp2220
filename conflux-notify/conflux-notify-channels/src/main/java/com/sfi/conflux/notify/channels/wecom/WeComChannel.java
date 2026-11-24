package com.sfi.conflux.notify.channels.wecom;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractWechatChannel;
import com.sfi.conflux.notify.channels.message.wecom.WeComAppChatMsg;
import com.sfi.conflux.notify.channels.message.wecom.WeComAppMsg;
import com.sfi.conflux.notify.channels.message.wecom.WeComLinkedCorpMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.wechat.WechatClients;
import me.chanjar.weixin.cp.api.WxCpService;

/**
 * 企业微信消息渠道：一份企微凭证覆盖应用/群聊/互联企业消息，按 bean 类型分发。
 *
 * <p>群机器人文本/图片/Markdown 等原生 API 收 String 参数（非 bean），不属于「继承 bean」范畴，
 * 本期不纳入；模板卡片可通过应用消息 {@code WxCpMessage} 的 msgType 承载。</p>
 *
 * @author lianghang
 */
public class WeComChannel extends AbstractWechatChannel {

    private final WechatClients clients;
    /** 测试注入用：直接给定原生 service，跳过凭证映射与平台层，便于 mock。 */
    private final WxCpService injectedService;

    public WeComChannel(WechatClients clients) {
        this.clients = clients;
        this.injectedService = null;
    }

    /** 供单测注入 mock service（生产不走此构造器）。 */
    public WeComChannel(WxCpService injectedService) {
        this.clients = null;
        this.injectedService = injectedService;
    }

    public WeComChannel() {
        this(new WechatClients());
    }

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_WECOM;
    }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        WxCpService service = injectedService != null ? injectedService : clients.cp(corpCredential(config));
        NotificationSendResult result = new NotificationSendResult();
        if (message instanceof WeComAppMsg) {
            result.setRaw(service.getMessageService().send((WeComAppMsg) message));
        } else if (message instanceof WeComAppChatMsg) {
            service.getChatService().sendMsg((WeComAppChatMsg) message);
        } else if (message instanceof WeComLinkedCorpMsg) {
            result.setRaw(service.getMessageService().sendLinkedCorpMessage((WeComLinkedCorpMsg) message));
        } else {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "企业微信渠道不支持的消息类型: {}", message.getClass().getName());
        }
        return result.setSuccess(true);
    }
}
