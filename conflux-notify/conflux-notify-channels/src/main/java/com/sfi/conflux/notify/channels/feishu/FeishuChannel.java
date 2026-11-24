package com.sfi.conflux.notify.channels.feishu;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.AbstractNotificationChannel;
import com.sfi.conflux.notify.channels.message.feishu.AbstractFeishuMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.core.PlatformCredential;
import com.sfi.conflux.platform.feishu.FeishuClients;

/**
 * 飞书通知渠道：一份应用凭证（appId + appSecret）覆盖 text/post/interactive/image/file，按 bean 类型分发。
 *
 * <p>飞书官方 SDK 的 content 是 JSON 字符串，故把消息 bean 的结构化字段用 common JsonUtils 序列化后
 * setContent，再交给原生 {@code client.im().message().create(req)}。access_token 由 SDK 自管。</p>
 *
 * @author lianghang
 */
public class FeishuChannel extends AbstractNotificationChannel {

    private final FeishuClients clients;
    /** 测试注入用：直接给定原生 client，跳过凭证映射与平台层，便于 mock。 */
    private final Client injectedClient;

    public FeishuChannel(FeishuClients clients) {
        this.clients = clients;
        this.injectedClient = null;
    }

    /** 供单测注入 mock client（生产不走此构造器）。 */
    public FeishuChannel(Client injectedClient) {
        this.clients = null;
        this.injectedClient = injectedClient;
    }

    public FeishuChannel() { this(new FeishuClients()); }

    @Override
    public String channelCode() { return NotifyChannels.FEISHU; }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        if (!(message instanceof AbstractFeishuMsg)) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "飞书渠道不支持的消息类型: {}", message.getClass().getName());
        }
        AbstractFeishuMsg msg = (AbstractFeishuMsg) message;
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(msg.getReceiveIdType())
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(msg.getReceiveId())
                        .msgType(msg.msgType())
                        .content(JsonUtils.toJson(msg.content()))
                        .build())
                .build();
        CreateMessageResp resp = client(config).im().message().create(req);

        NotificationSendResult result = new NotificationSendResult();
        result.setRaw(resp);
        result.setSuccess(resp != null && resp.success());
        return result;
    }

    private Client client(NotificationConfig config) {
        if (injectedClient != null) {
            return injectedClient;
        }
        return clients.client(PlatformCredential.of(
                attr(config, "appId"), attr(config, "appSecret"), config.getAttr()));
    }

    private static String attr(NotificationConfig config, String key) {
        return config.getAttr() == null ? null : config.getAttr().get(key);
    }
}
