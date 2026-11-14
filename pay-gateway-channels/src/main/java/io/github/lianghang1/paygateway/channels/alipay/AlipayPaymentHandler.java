package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
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
import java.util.stream.Collectors;

/**
 * 支付宝支付处理器：扫码（当面付）、H5（手机网站）、电脑网站、APP、
 * JSAPI（小程序/生活号，buyerId 取自 channelExtras.openid）五种下单方式，
 * 查单与关单。服务商模式（ISV）下单暂未支持。
 *
 * <p>页面类方式（H5/电脑网站）返回的 body 为支付宝自动提交表单，使用方原样
 * 输出给浏览器即可跳转；APP 方式返回 orderStr，交由支付宝客户端 SDK 唤起；
 * JSAPI 返回 tradeNo。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayPaymentHandler implements PaymentHandler {

    private final AlipayClientHolder holder;

    public AlipayPaymentHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        if (StringUtils.isNotBlank(config.getSubMchNo())) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝服务商模式支付暂未支持，请以普通商户模式接入");
        }
        AlipayParams params = AlipayClientHolder.paramsOf(config);
        Map<String, String> extras = StringUtils.isBlank(payment.getExtParams())
                ? null : JsonUtils.toMap(payment.getExtParams()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())));
        switch (payment.getMethod()) {
            case "ALI_QR":
                return precreate(payment, params, config);
            case "ALI_WAP":
                return wapPay(payment, params, config);
            case "ALI_PAGE":
                return pagePay(payment, params, config);
            case "ALI_APP":
                return appPay(payment, params, config);
            case "ALI_JSAPI":
                return tradeCreate(payment, params, config, extras);
            case "ALI_BAR":
                return barPay(payment, params, config, extras);
            default:
                throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                        "支付宝渠道不支持支付方式: {}", payment.getMethod());
        }
    }

    private PaymentInvocation precreate(Payment payment, AlipayParams params, ChannelConfig config) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        request.setBizModel(model);
        AlipayTradePrecreateResponse response = holder.execute("扫码下单", config, client -> client.execute(request));
        return new PaymentInvocation().setPayload(PayPayload.qrCode(response.getQrCode())).setState(PaymentState.PAYING);
    }

    private PaymentInvocation wapPay(Payment payment, AlipayParams params, ChannelConfig config) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        model.setProductCode("QUICK_WAP_WAY");
        request.setBizModel(model);
        String body = holder.execute("H5 下单", config, client -> client.pageExecute(request)).getBody();
        return new PaymentInvocation().setPayload(PayPayload.redirect(body)).setState(PaymentState.PAYING);
    }

    private PaymentInvocation pagePay(Payment payment, AlipayParams params, ChannelConfig config) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);
        String body = holder.execute("电脑网站下单", config, client -> client.pageExecute(request)).getBody();
        return new PaymentInvocation().setPayload(PayPayload.redirect(body)).setState(PaymentState.PAYING);
    }

    private PaymentInvocation appPay(Payment payment, AlipayParams params, ChannelConfig config) {
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);
        String body = holder.execute("APP 下单", config, client -> client.sdkExecute(request)).getBody();
        return new PaymentInvocation().setPayload(PayPayload.app(body)).setState(PaymentState.PAYING);
    }

    private PaymentInvocation tradeCreate(Payment payment, AlipayParams params, ChannelConfig config,
                                          Map<String, String> extras) {
        String buyerId = extras == null ? null : extras.get("openid");
        if (StringUtils.isBlank(buyerId)) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST,
                    "JSAPI 支付需在 channelExtras 中提供 openid（支付宝 buyerId）");
        }
        AlipayTradeCreateRequest request = new AlipayTradeCreateRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        AlipayTradeCreateModel model = new AlipayTradeCreateModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        model.setBuyerId(buyerId);
        request.setBizModel(model);
        AlipayTradeCreateResponse response = holder.execute("JSAPI 下单", config, client -> client.execute(request));
        return new PaymentInvocation().setPayload(PayPayload.jsapi(
                JsonUtils.toJson(new TradeNoPayload(response.getTradeNo())))).setState(PaymentState.PAYING);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(payment.getPaymentId());
        request.setBizModel(model);
        AlipayTradeQueryResponse response = holder.execute("查单", config, client -> client.execute(request));
        // 交易不存在（用户尚未唤起支付）按支付中处理，等待补单确认
        if (!response.isSuccess() && "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
            return new PaymentSnapshot().setState(PaymentState.PAYING).setRawMsg("ACQ.TRADE_NOT_EXIST");
        }
        return new PaymentSnapshot()
                .setChannelOrderNo(response.getTradeNo())
                .setState(AlipayStateTranslator.toPaymentState(response.getTradeStatus()))
                .setPaidAmount(AlipayStateTranslator.toFen(response.getTotalAmount()))
                .setSucceedTime(AlipayStateTranslator.toLocalDateTime(response.getSendPayDate()))
                .setRawMsg(response.getTradeStatus());
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(payment.getPaymentId());
        request.setBizModel(model);
        AlipayTradeCloseResponse response = holder.execute("关单", config, client -> client.execute(request));
        log.info("支付宝关单完成: paymentId={}, tradeNo={}", payment.getPaymentId(), response.getTradeNo());
    }

    /** 付款码支付：同步扣款，返回支付终态（无唤起参数）。 */
    private PaymentInvocation barPay(Payment payment, AlipayParams params, ChannelConfig config,
                                     Map<String, String> extras) {
        String authCode = extras == null ? null : extras.get("authCode");
        if (StringUtils.isBlank(authCode)) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST,
                    "付款码支付需在 channelExtras 中提供 authCode");
        }
        com.alipay.api.request.AlipayTradePayRequest request = new com.alipay.api.request.AlipayTradePayRequest();
        request.setNotifyUrl(params.getNotifyUrl());
        com.alipay.api.domain.AlipayTradePayModel model = new com.alipay.api.domain.AlipayTradePayModel();
        fillCommon(model::setOutTradeNo, model::setTotalAmount, model::setSubject, payment);
        model.setAuthCode(authCode);
        model.setScene("bar_code");
        request.setBizModel(model);
        com.alipay.api.response.AlipayTradePayResponse response =
                holder.execute("付款码支付", config, client -> client.execute(request));
        // 业务受理成功即扣款完成（失败在 execute 内以渠道异常抛出）
        return new PaymentInvocation()
                .setChannelOrderNo(response.getTradeNo())
                .setPayload(PayPayload.none())
                .setState(PaymentState.SUCCEEDED);
    }

    private void fillCommon(java.util.function.Consumer<String> outTradeNoSetter,
                            java.util.function.Consumer<String> totalAmountSetter,
                            java.util.function.Consumer<String> subjectSetter,
                            Payment payment) {
        outTradeNoSetter.accept(payment.getPaymentId());
        totalAmountSetter.accept(AlipayStateTranslator.toYuan(payment.getAmount()));
        subjectSetter.accept(StringUtils.isNotBlank(payment.getSubject()) ? payment.getSubject() : "pay-gateway");
    }

    /** JSAPI/APP 唤起参数结构（序列化为 JSON 交付使用方）。 */
    public static class TradeNoPayload {

        private final String tradeNo;

        public TradeNoPayload(String tradeNo) {
            this.tradeNo = tradeNo;
        }

        public String getTradeNo() {
            return tradeNo;
        }
    }
}
