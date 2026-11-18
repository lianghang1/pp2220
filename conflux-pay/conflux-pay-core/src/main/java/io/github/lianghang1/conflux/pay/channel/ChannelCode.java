package io.github.lianghang1.conflux.pay.channel;

/**
 * 内置渠道编码常量。
 *
 * <p>渠道编码为小写字符串；三方/银行间联渠道可自定义编码（字符串直传），
 * 并通过实现 {@link ChannelProvider} 接入，不局限于本类常量。</p>
 *
 * @author lianghang
 */
public final class ChannelCode {

    /** 微信支付 */
    public static final String WECHAT = "wechat";
    /** 支付宝 */
    public static final String ALIPAY = "alipay";
    /** 云闪付 */
    public static final String UNIONPAY = "unionpay";
    /** PayPal */
    public static final String PAYPAL = "paypal";
    /** 银联商务天满开放平台（聚合支付） */
    public static final String CHINAUMS = "chinaums";
    /** 模拟渠道（测试/演示） */
    public static final String MOCK = "mock";

    private ChannelCode() {
    }
}
