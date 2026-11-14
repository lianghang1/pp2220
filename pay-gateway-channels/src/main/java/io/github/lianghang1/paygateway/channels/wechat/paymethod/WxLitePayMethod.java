package io.github.lianghang1.paygateway.channels.wechat.paymethod;

import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.wechat.WechatParams;
import io.github.lianghang1.paygateway.model.Payment;

/**
 * 微信小程序支付：与公众号 JSAPI 走同一接口（V2 tradeType=JSAPI、
 * V3 /v3/pay/transactions/jsapi），独立 wayCode 便于按业务线区分路由与统计；
 * 唤起参数结构与 JSAPI 完全一致。
 *
 * @author lianghang
 */
public class WxLitePayMethod extends WxJsapiPayMethod {

    @Override
    public String method() {
        return PayMethod.WECHAT_LITE.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.paygateway.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        return super.createPayment(payment, config, params, service);
    }
}
