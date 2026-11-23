package com.sfi.conflux.pay.channels.wechat.paymethod;

import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.order.WxPayAppOrderResult;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.service.WxPayService;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentSnapshot;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channels.wechat.WechatParams;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.StringUtils;

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
    public PaymentInvocation createPayment(Payment payment, com.sfi.conflux.pay.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            return WechatPartnerSupport.createPartnerPaymentV3(payment, config, params, service, TradeTypeEnum.APP, null);
        }
        if (params.isV2()) {
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
            request.setOutTradeNo(payment.getBizOrderNo());
            request.setTotalFee(payment.getAmount().intValue());
            request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
            request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
            request.setTradeType("APP");
            WxPayAppOrderResult result = WxJsapiPayMethod.wrap("APP V2 下单", () ->
                    service.createOrder(WxPayConstants.TradeType.Specific.APP, request));
            return new PaymentInvocation().setPayload(PayPayload.app(JsonPayloads.toJson(result)))
                    .setState(PaymentState.PAYING);
        }
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setOutTradeNo(payment.getBizOrderNo());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
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
