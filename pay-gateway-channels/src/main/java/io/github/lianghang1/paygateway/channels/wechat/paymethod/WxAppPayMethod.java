package io.github.lianghang1.paygateway.channels.wechat.paymethod;

import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.order.WxPayAppOrderResult;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channel.PayMethod;
import io.github.lianghang1.paygateway.channels.wechat.WechatParams;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 微信 APP 支付：返回 App SDK 唤起参数（APP 类型 payload）。
 *
 * <p>V3：{@code createOrderV3(APP)} 返回签好名的 AppOrderV3Result；
 * V2：{@code createOrder(APP)} 返回 AppOrderResult，两者字段结构一致。</p>
 *
 * @author lianghang
 */
public class WxAppPayMethod implements WechatPayMethod {

    @Override
    public String method() {
        return PayMethod.WECHAT_APP.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.paygateway.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            return WechatPartnerSupport.createPartnerPaymentV3(payment, params, service, TradeTypeEnum.APP, null);
        }
        if (params.isV2()) {
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
            request.setOutTradeNo(payment.getPaymentId());
            request.setTotalFee(payment.getAmount().intValue());
            request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
            request.setNotifyUrl(params.getNotifyUrl());
            request.setTradeType("APP");
            WxPayAppOrderResult result = WxJsapiPayMethod.wrap("APP V2 下单", () ->
                    service.createOrder(WxPayConstants.TradeType.Specific.APP, request));
            return new PaymentInvocation().setPayload(PayPayload.app(JsonPayloads.toJson(result)))
                    .setState(PaymentState.PAYING);
        }
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setOutTradeNo(payment.getPaymentId());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        request.setNotifyUrl(params.getNotifyUrl());
        request.setAmount(WxJsapiPayMethod.v3Amount(payment));
        WxPayUnifiedOrderV3Result.AppResult result = WxJsapiPayMethod.wrap("APP V3 下单", () ->
                service.createOrderV3(TradeTypeEnum.APP, request));
        return new PaymentInvocation().setPayload(PayPayload.app(JsonPayloads.toJson(result)))
                .setState(PaymentState.PAYING);
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
