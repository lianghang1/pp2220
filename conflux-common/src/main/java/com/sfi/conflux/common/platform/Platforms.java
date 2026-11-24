package com.sfi.conflux.common.platform;

/**
 * 平台标识常量：平台层及各域对外统一用平台全称，禁止 wx / weixin / ali / ding 缩写。
 *
 * <p>放 conflux-common（所有域已依赖，零新增依赖）。用 final class + String 而非 enum——
 * 渠道编码开放、自定义渠道字符串直传，enum 会封死扩展（与 pay 现有 ChannelCode 同风格）。
 * 只收平台标识这几个词与缓存 key 前缀，不收各域渠道编码（wechat_open / wechat / alibaba 语义不同，属各域资产）。</p>
 *
 * @author lianghang
 */
public final class Platforms {

    public static final String WECHAT = "wechat";
    public static final String ALIPAY = "alipay";
    public static final String FEISHU = "feishu";
    public static final String DINGTALK = "dingtalk";

    private Platforms() {
    }
}
