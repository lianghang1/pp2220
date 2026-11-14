package io.github.lianghang1.conflux.pay.channels.wechat;

/**
 * 微信接口调用函数签名：允许抛出受检异常（WxJava 接口声明了
 * WxPayException），由各处理器的 wrap 统一收敛为网关渠道异常。
 *
 * @author lianghang
 */
public interface WechatCall<T> {

    /** 执行调用。 */
    T call() throws Exception;
}
