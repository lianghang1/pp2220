package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.service.payments.h5.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 微信支付处理器：JSAPI（公众号/小程序）、Native（扫码）、H5、APP 四种下单方式，
 * 查单与关单。服务商模式（subMchNo 存在）的下单暂未支持。
 *
 * <p>渠道附加参数（channelExtras）约定：JSAPI 必须携带 {@code openid}；
 * H5 建议携带 {@code payerClientIp}（缺省取订单 clientIp）。金额统一按分传递。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatPaymentHandler implements PaymentHandler {

    private final WechatClientHolder holder;

    public WechatPaymentHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        assertDirectMode(config);
        WechatParams params = WechatClientHolder.paramsOf(config);
        WechatClientHolder.ClientSet clients = holder.clients(config);
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        switch (payment.getMethod()) {
            case "WECHAT_JSAPI":
                return jsapi(payment, params, clients, extras);
            case "WECHAT_NATIVE":
                return nativePay(payment, params, clients);
            case "WECHAT_H5":
                return h5(payment, params, clients, extras);
            case "WECHAT_APP":
                return appPay(payment, params, clients, extras);
            default:
                throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                        "微信渠道不支持支付方式: {}", payment.getMethod());
        }
    }

    private PaymentInvocation jsapi(Payment payment, WechatParams params,
                                    WechatClientHolder.ClientSet clients, Map<String, String> extras) {
        String openid = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(openid)) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST, "JSAPI 支付需在 channelExtras 中提供 openid");
        }
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        fillCommonFields(request::setAppid, request::setMchid, request::setDescription,
                request::setOutTradeNo, request::setTimeExpire, request::setNotifyUrl, payment, params);
        com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency(payment.getCurrency());
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        PrepayWithRequestPaymentResponse response = wrap("JSAPI 下单",
                () -> clients.jsapiService().prepayWithRequestPayment(request));
        String json = JsonUtils.toJson(new JsapiPayload(
                response.getAppId(), response.getTimeStamp(), response.getNonceStr(),
                response.getPackageVal(), response.getSignType(), response.getPaySign()));
        return new PaymentInvocation().setPayload(PayPayload.jsapi(json)).setState(PaymentState.PAYING);
    }

    private PaymentInvocation nativePay(Payment payment, WechatParams params, WechatClientHolder.ClientSet clients) {
        com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        fillCommonFields(request::setAppid, request::setMchid, request::setDescription,
                request::setOutTradeNo, request::setTimeExpire, request::setNotifyUrl, payment, params);
        com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency(payment.getCurrency());
        request.setAmount(amount);

        PrepayResponse response = wrap("Native 下单", () -> clients.nativePayService().prepay(request));
        return new PaymentInvocation().setPayload(PayPayload.qrCode(response.getCodeUrl())).setState(PaymentState.PAYING);
    }

    private PaymentInvocation h5(Payment payment, WechatParams params,
                                 WechatClientHolder.ClientSet clients, Map<String, String> extras) {
        PrepayRequest request = new PrepayRequest();
        fillCommonFields(request::setAppid, request::setMchid, request::setDescription,
                request::setOutTradeNo, request::setTimeExpire, request::setNotifyUrl, payment, params);
        com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency(payment.getCurrency());
        request.setAmount(amount);
        com.wechat.pay.java.service.payments.h5.model.SceneInfo sceneInfo =
                new com.wechat.pay.java.service.payments.h5.model.SceneInfo();
        sceneInfo.setPayerClientIp(extras != null && StringUtils.isNotBlank(extras.get("payerClientIp"))
                ? extras.get("payerClientIp") : payment.getClientIp());
        sceneInfo.setH5Info(new com.wechat.pay.java.service.payments.h5.model.H5Info());
        request.setSceneInfo(sceneInfo);

        com.wechat.pay.java.service.payments.h5.model.PrepayResponse response =
                wrap("H5 下单", () -> clients.h5Service().prepay(request));
        return new PaymentInvocation().setPayload(PayPayload.redirect(response.getH5Url())).setState(PaymentState.PAYING);
    }

    private PaymentInvocation appPay(Payment payment, WechatParams params,
                                     WechatClientHolder.ClientSet clients, Map<String, String> extras) {
        com.wechat.pay.java.service.payments.app.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.app.model.PrepayRequest();
        fillCommonFields(request::setAppid, request::setMchid, request::setDescription,
                request::setOutTradeNo, request::setTimeExpire, request::setNotifyUrl, payment, params);
        com.wechat.pay.java.service.payments.app.model.Amount amount =
                new com.wechat.pay.java.service.payments.app.model.Amount();
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency(payment.getCurrency());
        request.setAmount(amount);

        com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse response =
                wrap("APP 下单", () -> clients.appService().prepayWithRequestPayment(request));
        String json = JsonUtils.toJson(new JsapiPayload(
                response.getAppid(), response.getTimestamp(), response.getNonceStr(),
                response.getPackageVal(), "RSA", response.getSign()));
        return new PaymentInvocation().setPayload(PayPayload.app(json)).setState(PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        assertDirectMode(config);
        WechatParams params = WechatClientHolder.paramsOf(config);
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(params.getMerchantId());
        request.setOutTradeNo(payment.getPaymentId());
        Transaction transaction = wrap("查单",
                () -> holder.clients(config).nativePayService().queryOrderByOutTradeNo(request));
        PaymentSnapshot snapshot = new PaymentSnapshot()
                .setChannelOrderNo(transaction.getTransactionId())
                .setState(WechatStateTranslator.toPaymentState(
                        transaction.getTradeState() == null ? null : transaction.getTradeState().name()))
                .setSucceedTime(WechatStateTranslator.toLocalDateTime(transaction.getSuccessTime()))
                .setRawMsg(transaction.getTradeStateDesc());
        if (transaction.getAmount() != null) {
            snapshot.setPaidAmount(transaction.getAmount().getTotal() == null ? null
                    : transaction.getAmount().getTotal().longValue());
        }
        return snapshot;
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        assertDirectMode(config);
        WechatParams params = WechatClientHolder.paramsOf(config);
        com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest request =
                new com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest();
        request.setMchid(params.getMerchantId());
        request.setOutTradeNo(payment.getPaymentId());
        wrap("关单", () -> {
            holder.clients(config).nativePayService().closeOrder(request);
            return null;
        });
    }

    private void fillCommonFields(java.util.function.Consumer<String> appidSetter,
                                  java.util.function.Consumer<String> mchidSetter,
                                  java.util.function.Consumer<String> descriptionSetter,
                                  java.util.function.Consumer<String> outTradeNoSetter,
                                  java.util.function.Consumer<String> timeExpireSetter,
                                  java.util.function.Consumer<String> notifyUrlSetter,
                                  Payment payment, WechatParams params) {
        appidSetter.accept(params.getAppId());
        mchidSetter.accept(params.getMerchantId());
        descriptionSetter.accept(StringUtils.isNotBlank(payment.getSubject())
                ? payment.getSubject() : "pay-gateway");
        outTradeNoSetter.accept(payment.getPaymentId());
        timeExpireSetter.accept(WechatStateTranslator.toWechatTime(payment.getExpiredTime()));
        notifyUrlSetter.accept(params.getNotifyUrl());
    }

    private void assertDirectMode(ChannelConfig config) {
        if (StringUtils.isNotBlank(config.getSubMchNo())) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信服务商模式支付暂未支持，请以普通商户模式接入");
        }
    }

    private <T> T wrap(String action, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }

    /** JSAPI/APP 唤起参数结构（序列化为 JSON 交付使用方）。 */
    public static class JsapiPayload {

        private final String appId;
        private final String timeStamp;
        private final String nonceStr;
        private final String packageVal;
        private final String signType;
        private final String paySign;

        public JsapiPayload(String appId, String timeStamp, String nonceStr,
                            String packageVal, String signType, String paySign) {
            this.appId = appId;
            this.timeStamp = timeStamp;
            this.nonceStr = nonceStr;
            this.packageVal = packageVal;
            this.signType = signType;
            this.paySign = paySign;
        }

        public String getAppId() {
            return appId;
        }

        public String getTimeStamp() {
            return timeStamp;
        }

        public String getNonceStr() {
            return nonceStr;
        }

        public String getPackageVal() {
            return packageVal;
        }

        public String getSignType() {
            return signType;
        }

        public String getPaySign() {
            return paySign;
        }
    }
}
