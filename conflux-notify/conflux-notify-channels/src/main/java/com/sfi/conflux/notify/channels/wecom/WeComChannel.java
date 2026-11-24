package com.sfi.conflux.notify.channels.wecom;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractWechatChannel;
import com.sfi.conflux.notify.channels.message.wecom.WeComAppChatMsg;
import com.sfi.conflux.notify.channels.message.wecom.WeComAppMsg;
import com.sfi.conflux.notify.channels.message.wecom.WeComLinkedCorpMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotCardMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotFileMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotImageMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotMarkdownMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotNewsMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotTextMsg;
import com.sfi.conflux.notify.channels.message.wecom.robot.WeComRobotVoiceMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.wechat.WechatClients;
import me.chanjar.weixin.cp.api.WxCpGroupRobotService;
import me.chanjar.weixin.cp.api.WxCpService;

/**
 * 企业微信消息渠道：一份企微凭证覆盖应用/群聊/互联企业/群机器人消息，按 bean 类型分发。
 *
 * <p>群机器人原生 API 收散装 String 参数，无原生 bean 可继承，故文本/Markdown/图片/文件/语音
 * 用自定义 bean 承载字段、渠道内拆参；图文/模板卡片可继承原生 bean（{@code NewArticle} /
 * {@code WxCpGroupRobotMessage}）直接传 SDK。webhookKey 随消息 bean 携带，不进凭证配置。</p>
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
        } else if (message instanceof WeComRobotTextMsg) {
            WeComRobotTextMsg m = (WeComRobotTextMsg) message;
            robot(service).sendText(m.getWebhookKey(), m.getContent(), m.getMentionedList(), m.getMentionedMobileList());
        } else if (message instanceof WeComRobotMarkdownMsg) {
            WeComRobotMarkdownMsg m = (WeComRobotMarkdownMsg) message;
            robot(service).sendMarkdown(m.getWebhookKey(), m.getContent());
        } else if (message instanceof WeComRobotImageMsg) {
            WeComRobotImageMsg m = (WeComRobotImageMsg) message;
            robot(service).sendImage(m.getWebhookKey(), m.getBase64(), m.getMd5());
        } else if (message instanceof WeComRobotFileMsg) {
            WeComRobotFileMsg m = (WeComRobotFileMsg) message;
            robot(service).sendFile(m.getWebhookKey(), m.getMediaId());
        } else if (message instanceof WeComRobotVoiceMsg) {
            WeComRobotVoiceMsg m = (WeComRobotVoiceMsg) message;
            robot(service).sendVoice(m.getWebhookKey(), m.getMediaId());
        } else if (message instanceof WeComRobotNewsMsg) {
            WeComRobotNewsMsg m = (WeComRobotNewsMsg) message;
            robot(service).sendNews(m.getWebhookKey(), m);
        } else if (message instanceof WeComRobotCardMsg) {
            WeComRobotCardMsg m = (WeComRobotCardMsg) message;
            robot(service).sendTemplateCardMessage(m.getWebhookKey(), m);
        } else {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "企业微信渠道不支持的消息类型: {}", message.getClass().getName());
        }
        return result.setSuccess(true);
    }

    private WxCpGroupRobotService robot(WxCpService service) {
        return service.getGroupRobotService();
    }
}
