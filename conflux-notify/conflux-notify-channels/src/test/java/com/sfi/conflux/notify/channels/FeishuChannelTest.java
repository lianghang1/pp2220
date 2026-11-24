package com.sfi.conflux.notify.channels;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.resource.Message;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.feishu.FeishuChannel;
import com.sfi.conflux.notify.channels.message.feishu.FeishuCardMsg;
import com.sfi.conflux.notify.channels.message.feishu.FeishuPostMsg;
import com.sfi.conflux.notify.channels.message.feishu.FeishuTextMsg;
import com.sfi.conflux.notify.config.NotificationConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 飞书通知渠道单测：验证 bean 字段序列化成 content JSON、msgType/receiveIdType 传递、响应归一。
 * 全部 mock SDK，不触发真实网络。
 *
 * @author lianghang
 */
class FeishuChannelTest {

    private NotificationConfig cfg() {
        return new NotificationConfig().setConfigId("app1").setChannelCode("feishu");
    }

    /** 搭好 client.im().message().create() 的 mock 链，返回给定响应。 */
    private Message mockMessage(CreateMessageResp resp) throws Exception {
        Client client = mock(Client.class);
        ImService imService = mock(ImService.class);
        Message message = mock(Message.class);
        when(client.im()).thenReturn(imService);
        when(imService.message()).thenReturn(message);
        when(message.create(any(CreateMessageReq.class))).thenReturn(resp);
        this.client = client;
        this.message = message;
        return message;
    }

    private Client client;
    private Message message;

    @Test
    void textMsgShouldSerializeContentAndPassMsgType() throws Exception {
        CreateMessageResp resp = mock(CreateMessageResp.class);
        when(resp.success()).thenReturn(true);
        mockMessage(resp);
        FeishuChannel channel = new FeishuChannel(client);

        FeishuTextMsg msg = new FeishuTextMsg();
        msg.setReceiveId("ou_xxx");
        msg.setText("你好飞书");
        NotificationSendResult result = channel.send(cfg(), msg);

        ArgumentCaptor<CreateMessageReq> cap = ArgumentCaptor.forClass(CreateMessageReq.class);
        verify(message).create(cap.capture());
        CreateMessageReq req = cap.getValue();
        assertThat(req.getReceiveIdType()).isEqualTo("open_id");
        assertThat(req.getCreateMessageReqBody().getMsgType()).isEqualTo("text");
        assertThat(req.getCreateMessageReqBody().getReceiveId()).isEqualTo("ou_xxx");
        assertThat(req.getCreateMessageReqBody().getContent()).isEqualTo("{\"text\":\"你好飞书\"}");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("feishu");
    }

    @Test
    void postMsgShouldBuildNestedContent() throws Exception {
        CreateMessageResp resp = mock(CreateMessageResp.class);
        when(resp.success()).thenReturn(true);
        mockMessage(resp);
        FeishuChannel channel = new FeishuChannel(client);

        FeishuPostMsg msg = new FeishuPostMsg();
        msg.setReceiveId("chat_xxx");
        msg.setReceiveIdType("chat_id");
        msg.setTitle("标题");
        msg.addTextLine("第一行");
        NotificationSendResult result = channel.send(cfg(), msg);

        ArgumentCaptor<CreateMessageReq> cap = ArgumentCaptor.forClass(CreateMessageReq.class);
        verify(message).create(cap.capture());
        CreateMessageReq req = cap.getValue();
        assertThat(req.getReceiveIdType()).isEqualTo("chat_id");
        assertThat(req.getCreateMessageReqBody().getMsgType()).isEqualTo("post");
        String content = req.getCreateMessageReqBody().getContent();
        assertThat(content).contains("\"zh_cn\"").contains("\"title\":\"标题\"")
                .contains("\"tag\":\"text\"").contains("第一行");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void cardMsgShouldPassCardJsonAsContent() throws Exception {
        CreateMessageResp resp = mock(CreateMessageResp.class);
        when(resp.success()).thenReturn(true);
        mockMessage(resp);
        FeishuChannel channel = new FeishuChannel(client);

        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("config", new LinkedHashMap<String, Object>());
        FeishuCardMsg msg = new FeishuCardMsg();
        msg.setReceiveId("ou_xxx");
        msg.setCard(card);
        NotificationSendResult result = channel.send(cfg(), msg);

        ArgumentCaptor<CreateMessageReq> cap = ArgumentCaptor.forClass(CreateMessageReq.class);
        verify(message).create(cap.capture());
        CreateMessageReq req = cap.getValue();
        assertThat(req.getCreateMessageReqBody().getMsgType()).isEqualTo("interactive");
        assertThat(req.getCreateMessageReqBody().getContent()).isEqualTo("{\"config\":{}}");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failedResponseShouldBeNormalized() throws Exception {
        CreateMessageResp resp = mock(CreateMessageResp.class);
        when(resp.success()).thenReturn(false);
        mockMessage(resp);
        FeishuChannel channel = new FeishuChannel(client);

        NotificationSendResult result = channel.send(cfg(), textMsg());

        assertThat(result.isSuccess()).isFalse();
    }

    private FeishuTextMsg textMsg() {
        FeishuTextMsg m = new FeishuTextMsg();
        m.setReceiveId("ou_x");
        m.setText("x");
        return m;
    }
}
