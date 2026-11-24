package com.sfi.conflux.notify.channels.mini;

import cn.binarywang.wx.miniapp.api.WxMaMsgService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channels.AbstractWechatChannel;
import com.sfi.conflux.notify.channels.message.mini.MiniKefuMsg;
import com.sfi.conflux.notify.channels.message.mini.MiniSubscribeMsg;
import com.sfi.conflux.notify.channels.message.mini.MiniUniformMsg;
import com.sfi.conflux.notify.channels.message.mini.MiniUpdatableMsg;
import com.sfi.conflux.notify.channels.support.NotifyChannels;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import com.sfi.conflux.platform.wechat.WechatClients;

/**
 * 微信小程序消息渠道：一份小程序凭证覆盖订阅/客服/统一/动态全部消息类型，按 bean 类型分发。
 *
 * @author lianghang
 */
public class WechatMiniChannel extends AbstractWechatChannel {

    private final WechatClients clients;
    /** 测试注入用：直接给定原生 service，跳过凭证映射与平台层，便于 mock。 */
    private final WxMaService injectedService;

    public WechatMiniChannel(WechatClients clients) {
        this.clients = clients;
        this.injectedService = null;
    }

    /** 供单测注入 mock service（生产不走此构造器）。 */
    public WechatMiniChannel(WxMaService injectedService) {
        this.clients = null;
        this.injectedService = injectedService;
    }

    public WechatMiniChannel() {
        this(new WechatClients());
    }

    @Override
    public String channelCode() {
        return NotifyChannels.WECHAT_MINI;
    }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) throws Exception {
        WxMaService service = injectedService != null ? injectedService : clients.ma(credential(config));
        WxMaMsgService msgService = service.getMsgService();
        if (message instanceof MiniSubscribeMsg) {
            msgService.sendSubscribeMsg((MiniSubscribeMsg) message);
        } else if (message instanceof MiniKefuMsg) {
            msgService.sendKefuMsg((MiniKefuMsg) message);
        } else if (message instanceof MiniUniformMsg) {
            msgService.sendUniformMsg((MiniUniformMsg) message);
        } else if (message instanceof MiniUpdatableMsg) {
            msgService.setUpdatableMsg((MiniUpdatableMsg) message);
        } else {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "小程序渠道不支持的消息类型: {}", message.getClass().getName());
        }
        return new NotificationSendResult().setSuccess(true);
    }
}
