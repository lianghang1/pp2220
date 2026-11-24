package com.sfi.conflux.notify.channels;

import cn.binarywang.wx.miniapp.api.WxMaMsgService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.message.mini.MiniSubscribeMsg;
import com.sfi.conflux.notify.channels.message.mp.MpTemplateMsg;
import com.sfi.conflux.notify.channels.message.wecom.WeComAppMsg;
import com.sfi.conflux.notify.channels.mini.WechatMiniChannel;
import com.sfi.conflux.notify.channels.mp.WechatMpChannel;
import com.sfi.conflux.notify.channels.wecom.WeComChannel;
import com.sfi.conflux.notify.config.NotificationConfig;
import me.chanjar.weixin.cp.api.WxCpMessageService;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.WxMpTemplateMsgService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 微信三大平台通知渠道单测：验证按 bean 类型分发到正确 SDK 方法、结果归一、上下文回填。
 * 全部 mock SDK，不触发真实网络。
 *
 * @author lianghang
 */
class WechatChannelsTest {

    private NotificationConfig cfg(String configId, String code) {
        return new NotificationConfig().setConfigId(configId).setChannelCode(code);
    }

    @Test
    void miniSubscribeMsgShouldDispatchToSubscribeService() throws Exception {
        WxMaService maService = mock(WxMaService.class);
        WxMaMsgService msgService = mock(WxMaMsgService.class);
        when(maService.getMsgService()).thenReturn(msgService);
        WechatMiniChannel channel = new WechatMiniChannel(maService);

        MiniSubscribeMsg msg = new MiniSubscribeMsg();
        NotificationSendResult result = channel.send(cfg("app1", "wechat_mini"), msg);

        verify(msgService).sendSubscribeMsg(msg);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("wechat_mini");
        assertThat(result.getConfigId()).isEqualTo("app1");
    }

    @Test
    void mpTemplateMsgShouldDispatchToTemplateService() throws Exception {
        WxMpService mpService = mock(WxMpService.class);
        WxMpTemplateMsgService tplService = mock(WxMpTemplateMsgService.class);
        when(mpService.getTemplateMsgService()).thenReturn(tplService);
        when(tplService.sendTemplateMsg(any())).thenReturn("MSG-ID-1");
        WechatMpChannel channel = new WechatMpChannel(mpService);

        MpTemplateMsg msg = new MpTemplateMsg();
        NotificationSendResult result = channel.send(cfg("app2", "wechat_mp"), msg);

        verify(tplService).sendTemplateMsg(msg);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRaw()).isEqualTo("MSG-ID-1");
        assertThat(result.getChannel()).isEqualTo("wechat_mp");
    }

    @Test
    void wecomAppMsgShouldDispatchToMessageService() throws Exception {
        WxCpService cpService = mock(WxCpService.class);
        WxCpMessageService msgService = mock(WxCpMessageService.class);
        when(cpService.getMessageService()).thenReturn(msgService);
        WeComChannel channel = new WeComChannel(cpService);

        WeComAppMsg msg = new WeComAppMsg();
        NotificationSendResult result = channel.send(cfg("app3", "wechat_wecom"), msg);

        verify(msgService).send(msg);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("wechat_wecom");
    }

    @Test
    void sdkExceptionShouldBeNormalizedToFailureResult() throws Exception {
        WxMaService maService = mock(WxMaService.class);
        WxMaMsgService msgService = mock(WxMaMsgService.class);
        when(maService.getMsgService()).thenReturn(msgService);
        org.mockito.Mockito.doThrow(new me.chanjar.weixin.common.error.WxErrorException("boom"))
                .when(msgService).sendSubscribeMsg(any());
        WechatMiniChannel channel = new WechatMiniChannel(maService);

        NotificationSendResult result = channel.send(cfg("app1", "wechat_mini"), new MiniSubscribeMsg());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("boom");
    }
}
