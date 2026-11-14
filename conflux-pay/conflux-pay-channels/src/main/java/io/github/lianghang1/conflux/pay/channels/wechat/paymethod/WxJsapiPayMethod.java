package io.github.lianghang1.conflux.pay.channels.wechat.paymethod;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryResult;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.wechat.WechatCall;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.wechat.WechatParams;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信 JSAPI 支付（公众号内支付；小程序同接口，见 {@link WxLitePayMethod}）。
 *
 * <p>V3：{@code createOrderV3(JSAPI)} 返回签好名的唤起参数（appId/timeStamp/
 * nonceStr/packageVal/signType/paySign）；V2：{@code createOrder(JSAPI)} 返回
 * 同结构的 MpOrderResult。JSAPI 必须在 channelExtras 中携带 {@code openid}。</p>
 *
 * @author lianghang
 */
public class WxJsapiPayMethod implements WechatPayMethod {

    @Override
    public String method() {
        return PayMethod.WECHAT_JSAPI.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.conflux.pay.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : flatExtras(payment.getExtParams());
        String openid = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(openid)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "JSAPI 支付需在 channelExtras 中提供 openid");
        }
        if (WechatPartnerSupport.isIsv(config)) {
            return WechatPartnerSupport.createPartnerPaymentV3(payment, config, params, service,
                    TradeTypeEnum.JSAPI, openid);
        }
        if (params.isV2()) {
            WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
            request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
            request.setOutTradeNo(payment.getPaymentId());
            request.setTotalFee(payment.getAmount().intValue());
            request.setSpbillCreateIp(StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
            request.setNotifyUrl(config.getNotifyUrl());
            request.setTradeType(WxPayConstants.TradeType.JSAPI);
            request.setOpenid(openid);
            WxPayMpOrderResult result = wrap("JSAPI V2 下单", () ->
                    service.createOrder(WxPayConstants.TradeType.Specific.JSAPI, request));
            return new PaymentInvocation().setPayload(PayPayload.jsapi(JsonUtils.toJson(result)))
                    .setState(PaymentState.PAYING);
        }
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setOutTradeNo(payment.getPaymentId());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        request.setNotifyUrl(config.getNotifyUrl());
        request.setAmount(v3Amount(payment));
        WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        WxPayUnifiedOrderV3Result.JsapiResult result = wrap("JSAPI V3 下单", () ->
                service.createOrderV3(TradeTypeEnum.JSAPI, request));
        return new PaymentInvocation().setPayload(PayPayload.jsapi(JsonUtils.toJson(result)))
                .setState(PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, WechatParams params, WxPayService service) {
        if (params.isV2()) {
            WxPayOrderQueryResult result = wrap("V2 查单", () ->
                    service.queryOrder(payment.getPaymentId(), null));
            return new PaymentSnapshot()
                    .setChannelOrderNo(result.getTransactionId())
                    .setState(WechatStateBridge.toPaymentState(result.getTradeState()))
                    .setPaidAmount(result.getTotalFee() == null ? null : result.getTotalFee().longValue())
                    .setSucceedTime(WechatStateBridge.toLocalDateTime(result.getTimeEnd()))
                    .setRawMsg(result.getTradeState());
        }
        WxPayOrderQueryV3Result result = wrap("V3 查单", () -> {
            WxPayOrderQueryV3Request request = new WxPayOrderQueryV3Request();
            request.setOutTradeNo(payment.getPaymentId());
            return service.queryOrderV3(request);
        });
        return new PaymentSnapshot()
                .setChannelOrderNo(result.getTransactionId())
                .setState(WechatStateBridge.toPaymentState(result.getTradeState()))
                .setSucceedTime(WechatStateBridge.toLocalDateTime(result.getSuccessTime()))
                .setRawMsg(result.getTradeState());
    }

    @Override
    public void closePayment(Payment payment, WechatParams params, WxPayService service) {
        if (params.isV2()) {
            wrap("V2 关单", () -> {
                service.closeOrder(payment.getPaymentId());
                return null;
            });
            return;
        }
        wrap("V3 关单", () -> {
            service.closeOrderV3(payment.getPaymentId());
            return null;
        });
    }

    static WxPayUnifiedOrderV3Request.Amount v3Amount(Payment payment) {
        WxPayUnifiedOrderV3Request.Amount amount = new WxPayUnifiedOrderV3Request.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency("CNY");
        return amount;
    }

    static Map<String, String> flatExtras(String extParams) {
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<String, Object> entry : JsonUtils.toMap(extParams).entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }

    static <T> T wrap(String action, WechatCall<T> call) {
        try {
            return call.call();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
