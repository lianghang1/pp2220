package com.sfi.conflux.pay.channels.wechat;

/**
 * 微信支付 API 基地址常量（商户 API，区别于渠道下单网关）。
 *
 * @author lianghang
 */
final class WechatApiBase {

    /** 商户 APIv3 基地址 */
    static final String PAY_BASE_URL = "https://api.mch.weixin.qq.com";

    private WechatApiBase() {
    }
}
