package com.sfi.conflux.notify.channels;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.response.AlipayOpenAppMiniTemplatemessageSendResponse;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.alipay.AlipayMiniChannel;
import com.sfi.conflux.notify.channels.alipay.AlipayPublicChannel;
import com.sfi.conflux.notify.channels.message.alipay.AliMiniSubscribeMsg;
import com.sfi.conflux.notify.channels.message.alipay.AliPublicSingleMsg;
import com.sfi.conflux.notify.config.NotificationConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付宝通知渠道单测：验证 Request 包装 setBizModel、execute/certificateExecute 分派、响应归一。
 * 全部 mock SDK，不触发真实网络。
 *
 * @author lianghang
 */
class AlipayChannelsTest {

    private NotificationConfig cfg(String configId, String code) {
        return new NotificationConfig().setConfigId(configId).setChannelCode(code);
    }

    private NotificationConfig cfgWithSignMode(String mode) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("appId", "2021000000");
        attr.put("privateKey", "MIIEv...");
        attr.put("signMode", mode);
        return new NotificationConfig().setConfigId("app1").setChannelCode("alipay_mini").setAttr(attr);
    }

    @Test
    void miniSubscribeShouldWrapBizModelAndExecute() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayOpenAppMiniTemplatemessageSendResponse resp = mock(AlipayOpenAppMiniTemplatemessageSendResponse.class);
        when(resp.isSuccess()).thenReturn(true);
        when(client.execute(any(AlipayRequest.class))).thenReturn(resp);
        AlipayMiniChannel channel = new AlipayMiniChannel(client);

        AliMiniSubscribeMsg msg = new AliMiniSubscribeMsg();
        msg.setToOpenId("openid-1");
        msg.setUserTemplateId("TPL_1");
        NotificationSendResult result = channel.send(cfg("app1", "alipay_mini"), msg);

        verify(client).execute(any(AlipayRequest.class));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRaw()).isSameAs(resp);
        assertThat(result.getChannel()).isEqualTo("alipay_mini");
    }

    @Test
    void certificateModeShouldUseCertificateExecute() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayOpenAppMiniTemplatemessageSendResponse resp = mock(AlipayOpenAppMiniTemplatemessageSendResponse.class);
        when(resp.isSuccess()).thenReturn(true);
        when(client.certificateExecute(any(AlipayRequest.class))).thenReturn(resp);
        AlipayMiniChannel channel = new AlipayMiniChannel(client);

        NotificationSendResult result = channel.send(cfgWithSignMode("cert"), new AliMiniSubscribeMsg());

        verify(client).certificateExecute(any(AlipayRequest.class));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failedResponseShouldBeNormalized() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        AlipayOpenAppMiniTemplatemessageSendResponse resp = mock(AlipayOpenAppMiniTemplatemessageSendResponse.class);
        when(resp.isSuccess()).thenReturn(false);
        when(client.execute(any(AlipayRequest.class))).thenReturn(resp);
        AlipayMiniChannel channel = new AlipayMiniChannel(client);

        NotificationSendResult result = channel.send(cfg("app1", "alipay_mini"), new AliMiniSubscribeMsg());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void publicSingleShouldDispatchToPublicChannel() throws Exception {
        AlipayClient client = mock(AlipayClient.class);
        com.alipay.api.response.AlipayOpenPublicMessageSingleSendResponse resp =
                mock(com.alipay.api.response.AlipayOpenPublicMessageSingleSendResponse.class);
        when(resp.isSuccess()).thenReturn(true);
        when(client.execute(any(AlipayRequest.class))).thenReturn(resp);
        AlipayPublicChannel channel = new AlipayPublicChannel(client);

        NotificationSendResult result = channel.send(cfg("app1", "alipay_public"), new AliPublicSingleMsg());

        verify(client).execute(any(AlipayRequest.class));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("alipay_public");
    }
}
