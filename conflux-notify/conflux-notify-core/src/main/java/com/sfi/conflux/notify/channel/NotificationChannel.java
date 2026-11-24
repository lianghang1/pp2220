package com.sfi.conflux.notify.channel;

import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.config.NotificationConfig;

/**
 * 通知渠道统一 SPI：新增任意推送平台（短信 / 微信 / 飞书 / 钉钉 / 支付宝…）的唯一扩展点。
 *
 * <p>与登录域 {@code AuthChannel} 同构：框架只认两个行为——{@link #channelCode()} 定位渠道、
 * {@link #send} 执行发送。渠道内部用什么 SDK、什么协议、什么参数名完全自由。门面只做路由，
 * 一张 {@code Map<channelCode, NotificationChannel>} 承载全部内置与自定义渠道，新增渠道不改门面 API。</p>
 *
 * <p>内置渠道（如短信 sms4j、未来的 IM 平台）由 SPI 在网关 build 时自动注册；使用方自定义渠道
 * 继承 {@link AbstractNotificationChannel} 或实现本接口，经 {@code registerChannel} / builder.channel 声明。</p>
 *
 * @author lianghang
 */
public interface NotificationChannel {

    /** 该渠道负责的形态编码，如 alibaba（短信）/ wechat_subscribe（微信订阅消息）。全局唯一。 */
    String channelCode();

    /** 执行一次发送：渠道内部自行完成配置装载、SDK 调用与结果归一。 */
    NotificationSendResult send(NotificationConfig config, NotificationSendRequest request);
}
