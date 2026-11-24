package com.sfi.conflux.notify.api;

/**
 * 统一通知消息标记接口：所有通知入参（短信、微信全家桶、未来 IM）一律实现本接口。
 *
 * <p>门面只有唯一入口 {@code send(configId, NotificationMessage)}，渠道编码由消息自带——
 * 消息对象决定「用哪个平台的凭证」，configId 决定「用该平台的哪份配置」，职责分明。</p>
 *
 * <p>{@link #channelCode()} 标识一份凭证 / 一个应用形态，不是消息类型：同一应用下多种消息
 * （如微信小程序订阅/客服/统一）共用一个 code，具体消息类型由消息对象的类型区分。
 * 编码命名遵循框架契约 C5。</p>
 *
 * @author lianghang
 */
public interface NotificationMessage {

    /** 渠道编码，如 alibaba（短信供应商）/ wechat_mini（微信小程序）。 */
    String channelCode();
}
