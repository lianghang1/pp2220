package io.github.lianghang1.conflux.pay.channels.wechat.paymethod;

import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.channels.wechat.WechatParams;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.conflux.pay.model.Payment;

/**
 * 微信支付方式契约：一种支付方式一个实现类，
 * 由渠道入口按 wayCode 路由。实现内按渠道参数的 wxApiVersion
 * 选择 V2/V3 协议路径。
 *
 * <p>实现要求：无状态、线程安全；渠道异常统一包装为
 * GatewayException(CHANNEL_CALL_FAILED) 上抛。</p>
 *
 * @author lianghang
 */
public interface WechatPayMethod {

    /** 支付方式编码（PayMethod.code）。 */
    String method();

    /** 发起渠道支付。 */
    PaymentInvocation createPayment(Payment payment, io.github.lianghang1.conflux.pay.channel.ChannelConfig config, WechatParams params, WxPayService service);

    /** 查询渠道侧订单快照（补单/对账）。 */
    PaymentSnapshot queryPayment(Payment payment, WechatParams params, WxPayService service);

    /** 关闭渠道订单。 */
    void closePayment(Payment payment, WechatParams params, WxPayService service);
}
