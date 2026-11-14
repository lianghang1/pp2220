package io.github.lianghang1.paygateway.channels.wechat.paymethod;


import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
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
 * 微信 H5 支付（手机浏览器跳转）：返回支付跳转链接（REDIRECT）。
 *
 * <p>V3：{@code createOrderV3(H5)} 返回 h5_url（scene_info 需携带支付场景，
 * 由 channelExtras 可选覆盖）；V2：{@code createOrder(MWEB)} 返回 mweb_url。</p>
 *
 * @author lianghang
 */
public class WxH5PayMethod implements WechatPayMethod {

    @Override
    public String method() {
        return "WECHAT_H5";
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.paygateway.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            return WechatPartnerSupport.createPartnerPaymentV3(payment, params, service, TradeTypeEnum.H5, null);
        }
        if (params.isV2()) {
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
            request.setOutTradeNo(payment.getPaymentId());
            request.setTotalFee(payment.getAmount().intValue());
            request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
            request.setNotifyUrl(params.getNotifyUrl());
            request.setTradeType("MWEB");
            WxPayMwebOrderResult result = WxJsapiPayMethod.wrap("H5 V2 下单", () ->
                    service.createOrder(WxPayConstants.TradeType.Specific.MWEB, request));
            return new PaymentInvocation().setPayload(PayPayload.redirect(result.getMwebUrl()))
                    .setState(PaymentState.PAYING);
        }
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setOutTradeNo(payment.getPaymentId());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        request.setNotifyUrl(params.getNotifyUrl());
        request.setAmount(WxJsapiPayMethod.v3Amount(payment));
        WxPayUnifiedOrderV3Request.SceneInfo sceneInfo = new WxPayUnifiedOrderV3Request.SceneInfo();
        sceneInfo.setPayerClientIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
        request.setSceneInfo(sceneInfo);
        String h5Url = WxJsapiPayMethod.wrap("H5 V3 下单", () ->
                service.createOrderV3(TradeTypeEnum.H5, request));
        return new PaymentInvocation().setPayload(PayPayload.redirect(h5Url)).setState(PaymentState.PAYING);
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
