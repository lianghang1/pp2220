package com.sfi.conflux.notify.channels.support;

import com.sfi.conflux.common.platform.Platforms;

/**
 * 通知渠道编码常量（遵循契约 C5 命名口径）：平台全称前缀 + 应用形态后缀。
 *
 * <p>编码 = 一份凭证 / 一个应用形态，不是消息类型；同一 code 下多种消息由 bean 类型区分。
 * 与登录域既有编码复用同一套（如 wechat_mp 公众号凭证：登录做网页授权、通知发模板消息）。</p>
 *
 * @author lianghang
 */
public final class NotifyChannels {

    /** 微信公众号（appId + secret） */
    public static final String WECHAT_MP = Platforms.WECHAT + "_mp";
    /** 微信小程序（appId + secret） */
    public static final String WECHAT_MINI = Platforms.WECHAT + "_mini";
    /** 企业微信（corpId + corpSecret + agentId） */
    public static final String WECHAT_WECOM = Platforms.WECHAT + "_wecom";

    private NotifyChannels() {
    }
}
