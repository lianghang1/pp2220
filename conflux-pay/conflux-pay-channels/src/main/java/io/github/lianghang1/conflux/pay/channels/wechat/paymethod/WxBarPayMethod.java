package io.github.lianghang1.conflux.pay.channels.wechat.paymethod;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest;
import com.github.binarywang.wxpay.bean.result.WxPayMicropayResult;
import com.github.binarywang.wxpay.service.WxPayService;
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
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.HashMap;
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
        return PayMethod.WECHAT_BAR.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, io.github.lianghang1.conflux.pay.channel.ChannelConfig config, WechatParams params, WxPayService service) {
        if (WechatPartnerSupport.isIsv(config)) {
            return isvMicropay(payment, config, params);
        }
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : WxJsapiPayMethod.flatExtras(payment.getExtParams());
        String authCode = extras == null ? null : extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        WxPayMicropayRequest request = new WxPayMicropayRequest();
        request.setBody(StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        request.setOutTradeNo(payment.getBizOrderNo());
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

    /**
     * 服务商模式付款码：V2 micropay 报文携带 sub_appid/sub_mchid
     * （WxJava 普通商户模型未含子商户字段，经 V2 直连客户端实现）。
     */
    private PaymentInvocation isvMicropay(Payment payment,
                                          io.github.lianghang1.conflux.pay.channel.ChannelConfig config,
                                          WechatParams params) {
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? new HashMap<String, String>() : WxJsapiPayMethod.flatExtras(payment.getExtParams());
        String authCode = extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(CommonErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("appid", params.getAppId());
        fields.put("mch_id", params.getMerchantId());
        fields.put("sub_appid", StringUtils.defaultIfBlank(params.getSubAppId(), params.getAppId()));
        fields.put("sub_mch_id", config.getSubMchNo());
        fields.put("body", StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        fields.put("out_trade_no", payment.getBizOrderNo());
        fields.put("total_fee", String.valueOf(payment.getAmount()));
        fields.put("spbill_create_ip", StringUtils.defaultIfBlank(payment.getClientIp(), "127.0.0.1"));
        fields.put("auth_code", authCode);
        Map<String, String> result = new io.github.lianghang1.conflux.pay.channels.wechat.WechatV2Client()
                .execute(params, "/pay/micropay", fields);
        boolean success = "SUCCESS".equals(result.get("result_code"))
                && "SUCCESS".equals(result.get("trade_state"));
        return new PaymentInvocation()
                .setChannelOrderNo(result.get("transaction_id"))
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
