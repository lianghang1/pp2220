package io.github.lianghang1.paygateway.channels.wechat.paymethod;

import com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest;
import com.github.binarywang.wxpay.bean.result.WxPayMicropayResult;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channels.wechat.WechatParams;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.util.Map;

/**
 * 微信付款码支付（收银员扫用户付款码）：同步扣款，走 V2 {@code micropay}
 * 接口（WxJava 原生支持；该接口在 V3 协议下 SDK 未封装，故不受 wxApiVersion 路由影响）。
 *
 * <p>channelExtras 约定：{@code authCode} 必填（用户付款码），{@code termId} 选填。</p>
 *
 * @author lianghang
 */
public class WxBarPayMethod implements WechatPayMethod {

    @Override
    public String method() {
        return "WECHAT_BAR";
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.paygateway.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "微信付款码服务商模式走 V2 子商户配置，暂未支持");
        }
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : WxJsapiPayMethod.flatExtras(payment.getExtParams());
        String authCode = extras == null ? null : extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        WxPayMicropayRequest request = new WxPayMicropayRequest();
        request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        request.setOutTradeNo(payment.getPaymentId());
        request.setTotalFee(payment.getAmount().intValue());
        request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
        request.setAuthCode(authCode);
        // 付款码支付无独立通知地址，终态经查询/事件确认
        WxPayMicropayResult result = WxJsapiPayMethod.wrap("付款码支付", () -> service.micropay(request));
        boolean success = "SUCCESS".equals(result.getResultCode());
        return new PaymentInvocation()
                .setChannelOrderNo(result.getTransactionId())
                .setPayload(PayPayload.none())
                .setState(success ? PaymentState.SUCCEEDED : PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, WechatParams params, WxPayService service) {
        // 付款码支付结果以同步返回为准，挂单场景经查单（JSAPI 同款查询）确认
        return new WxJsapiPayMethod().queryPayment(payment, params, service);
    }

    @Override
    public void closePayment(Payment payment, WechatParams params, WxPayService service) {
        new WxJsapiPayMethod().closePayment(payment, params, service);
    }
}
