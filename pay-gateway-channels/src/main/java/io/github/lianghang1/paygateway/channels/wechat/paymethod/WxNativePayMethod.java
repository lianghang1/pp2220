package io.github.lianghang1.paygateway.channels.wechat.paymethod;


import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.order.WxPayNativeOrderResult;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channels.wechat.WechatParams;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 微信 Native 支付（WEB 扫码）：返回二维码码值，使用方渲染二维码引导扫码。
 *
 * <p>V3：{@code createOrderV3(NATIVE)} 返回 code_url；V2：{@code createOrder(NATIVE)}
 * 返回 code_url。</p>
 *
 * @author lianghang
 */
public class WxNativePayMethod implements WechatPayMethod {

    @Override
    public String method() {
        return "WECHAT_NATIVE";
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.paygateway.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            return WechatPartnerSupport.createPartnerPaymentV3(payment, params, service, TradeTypeEnum.NATIVE, null);
        }
        if (params.isV2()) {
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
            request.setOutTradeNo(payment.getPaymentId());
            request.setTotalFee(payment.getAmount().intValue());
            request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
            request.setNotifyUrl(params.getNotifyUrl());
            request.setTradeType("NATIVE");
            WxPayNativeOrderResult result = WxJsapiPayMethod.wrap("Native V2 下单", () ->
                    service.createOrder(WxPayConstants.TradeType.Specific.NATIVE, request));
            return new PaymentInvocation().setPayload(PayPayload.qrCode(result.getCodeUrl()))
                    .setState(PaymentState.PAYING);
        }
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setOutTradeNo(payment.getPaymentId());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        request.setNotifyUrl(params.getNotifyUrl());
        request.setAmount(WxJsapiPayMethod.v3Amount(payment));
        String codeUrl = WxJsapiPayMethod.wrap("Native V3 下单", () ->
                service.createOrderV3(TradeTypeEnum.NATIVE, request));
        return new PaymentInvocation().setPayload(PayPayload.qrCode(codeUrl)).setState(PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, WechatParams params, WxPayService service) {
        return new WxJsapiPayMethod().queryPayment(payment, params, service);
    }

    @Override
    public void closePayment(Payment payment, WechatParams params, WxPayService service) {
        new WxJsapiPayMethod().closePayment(payment, params, service);
    }
}
