package io.github.lianghang1.paygateway.channels.wechat;

import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxAppPayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxBarPayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxH5PayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxJsapiPayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxLitePayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WxNativePayMethod;
import io.github.lianghang1.paygateway.channels.wechat.paymethod.WechatPayMethod;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 微信支付处理器门面：按支付方式路由到独立的 PayMethod 实现
 * （JSAPI 公众号/Lite 小程序/Native 扫码/H5/APP/Bar 付款码），并透传查单与关单。
 *
 * <p>V2/V3 协议由各 PayMethod 按渠道参数的 wxApiVersion 自行路由；
 * 付款码固定走 V2 micropay。服务商模式（subMchNo）随配置透传 WxJava。</p>
 *
 * @author lianghang
 */
public class WechatPaymentHandler implements PaymentHandler {

    private final WechatClientHolder holder = new WechatClientHolder();
    private final Map<String, WechatPayMethod> payMethods = new HashMap<String, WechatPayMethod>();

    public WechatPaymentHandler() {
        register(new WxJsapiPayMethod());
        register(new WxLitePayMethod());
        register(new WxNativePayMethod());
        register(new WxH5PayMethod());
        register(new WxAppPayMethod());
        register(new WxBarPayMethod());
    }

    private void register(WechatPayMethod payMethod) {
        payMethods.put(payMethod.method(), payMethod);
    }

    /** 渠道已支持的支付方式集合（能力声明/调试用）。 */
    public Set<String> supportedMethods() {
        return payMethods.keySet();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        WechatPayMethod payMethod = payMethodOf(payment);
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        return payMethod.createPayment(payment, config, params, service);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        WechatPayMethod payMethod = payMethodOf(payment);
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        return payMethod.queryPayment(payment, params, service);
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        WechatPayMethod payMethod = payMethodOf(payment);
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        payMethod.closePayment(payment, params, service);
    }

    private WechatPayMethod payMethodOf(Payment payment) {
        WechatPayMethod payMethod = payMethods.get(payment.getMethod());
        if (payMethod == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "微信渠道不支持支付方式: {}", payment.getMethod());
        }
        return payMethod;
    }
}
