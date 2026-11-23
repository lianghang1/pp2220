package com.sfi.conflux.pay.channels.wechat.paymethod;

import com.github.binarywang.wxpay.bean.request.WxPayPartnerUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.service.WxPayService;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channels.wechat.WechatParams;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

import java.util.Map;

/**
 * 微信服务商（ISV）V3 下单辅助：特约商户号随 ChannelConfig 传入，
 * 服务商应用/商户号随渠道参数传入，特约商户 AppID 可选。
 *
 * <p>下单经 unifiedPartnerOrderV3 后，JSAPI/APP 的唤起参数以商户私钥经
 * getPayInfo 现场签名构建；openid 归属按特约商户 AppID 是否配置
 * 选择 subOpenid / spOpenid。付款码（V2 micropay）不支持服务商模式。</p>
 *
 * @author lianghang
 */
final class WechatPartnerSupport {

    private WechatPartnerSupport() {
    }

    /** 是否服务商模式（特约商户号已配置）。 */
    static boolean isIsv(com.sfi.conflux.pay.channel.ChannelConfig config) {
        return StringUtils.isNotBlank(config.getSubMchNo());
    }

    /**
     * 服务商 V3 统一下单并归一化唤起结果：JSAPI/LITE → JsapiResult（JSAPI payload）、
     * NATIVE → codeUrl（QRCODE）、H5 → h5Url（REDIRECT）、APP → AppResult（APP payload）。
     *
     * @param openid JSAPI 场景的付款用户 openid（可空）
     */
    static PaymentInvocation createPartnerPaymentV3(Payment payment, com.sfi.conflux.pay.channel.ChannelConfig config, WechatParams params,
                                                    WxPayService service, TradeTypeEnum tradeType,
                                                    String openid) {
        WxPayPartnerUnifiedOrderV3Request request = new WxPayPartnerUnifiedOrderV3Request();
        request.setSpAppid(params.getAppId());
        request.setSpMchId(params.getMerchantId());
        request.setSubAppid(params.getSubAppId());
        request.setOutTradeNo(payment.getBizOrderNo());
        request.setDescription(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        request.setNotifyUrl(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
        WxPayPartnerUnifiedOrderV3Request.Amount amount = new WxPayPartnerUnifiedOrderV3Request.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        if (StringUtils.isNotBlank(openid)) {
            WxPayPartnerUnifiedOrderV3Request.Payer payer = new WxPayPartnerUnifiedOrderV3Request.Payer();
            if (StringUtils.isNotBlank(params.getSubAppId())) {
                payer.setSubOpenid(openid);
            } else {
                payer.setSpOpenid(openid);
            }
            request.setPayer(payer);
        }
        try {
            WxPayUnifiedOrderV3Result result = service.unifiedPartnerOrderV3(tradeType, request);
            // JSAPI/APP 的唤起参数需以商户私钥现场签名（服务商下单仅返回 prepayId）
            String signAppId = StringUtils.isNotBlank(params.getSubAppId())
                    ? params.getSubAppId() : params.getAppId();
            java.security.PrivateKey privateKey = service.getConfig().getPrivateKey();
            switch (tradeType) {
                case NATIVE:
                    return new PaymentInvocation().setPayload(PayPayload.qrCode(result.getCodeUrl()))
                            .setState(PaymentState.PAYING);
                case H5:
                    return new PaymentInvocation().setPayload(PayPayload.redirect(result.getH5Url()))
                            .setState(PaymentState.PAYING);
                case APP:
                    return new PaymentInvocation()
                            .setPayload(PayPayload.app(JsonUtils.toJson(result.getPayInfo(
                                    TradeTypeEnum.APP, signAppId, params.getMerchantId(), privateKey))))
                            .setState(PaymentState.PAYING);
                case JSAPI:
                default:
                    return new PaymentInvocation()
                            .setPayload(PayPayload.jsapi(JsonUtils.toJson(result.getPayInfo(
                                    TradeTypeEnum.JSAPI, signAppId, params.getMerchantId(), privateKey))))
                            .setState(PaymentState.PAYING);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信服务商下单失败: {}", e.getMessage(), e);
        }
    }

    /** flat 辅助（与 WxJsapiPayMethod 保持一致语义）。 */
    static Map<String, String> flatExtras(String extParams) {
        Map<String, String> result = new java.util.HashMap<String, String>();
        for (Map.Entry<String, Object> entry : JsonUtils.toMap(extParams).entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }
}
