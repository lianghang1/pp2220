package com.sfi.conflux.pay.channels.alipay;

import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PaymentHandler;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PaymentSnapshot;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliAppPayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliBarPayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliJsapiPayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliPagePayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliQrPayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AliWapPayMethod;
import com.sfi.conflux.pay.channels.alipay.paymethod.AlipayPayMethod;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝支付处理器门面：按支付方式路由到独立的 PayMethod 实现
 * （扫码/付款码/手机网站/电脑网站/APP/JSAPI），查单与关单统一按商户订单号处理。
 *
 * <p>服务商模式（subMchNo 存在）支付下单暂未支持。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayPaymentHandler implements PaymentHandler {

    private final AlipayClientHolder holder = new AlipayClientHolder();
    private final Map<String, AlipayPayMethod> payMethods = new HashMap<String, AlipayPayMethod>();

    public AlipayPaymentHandler() {
        register(new AliQrPayMethod());
        register(new AliBarPayMethod());
        register(new AliWapPayMethod());
        register(new AliPagePayMethod());
        register(new AliAppPayMethod());
        register(new AliJsapiPayMethod());
    }

    private void register(AlipayPayMethod payMethod) {
        payMethods.put(payMethod.method(), payMethod);
    }

    /** 渠道已支持的支付方式集合（能力声明/调试用）。 */
    public Set<String> supportedMethods() {
        return payMethods.keySet();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        AlipayPayMethod payMethod = payMethodOf(payment);
        return payMethod.createPayment(payment, config, holder);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        request.setBizModel(model);
        AlipayTradeQueryResponse response = holder.executeRaw("查单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
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
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        request.setBizModel(model);
        AlipayTradeCloseResponse response = holder.execute("关单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        log.info("支付宝关单完成: paymentId={}, tradeNo={}", payment.getBizOrderNo(), response.getTradeNo());
    }

    private AlipayPayMethod payMethodOf(Payment payment) {
        AlipayPayMethod payMethod = payMethods.get(payment.getMethod());
        if (payMethod == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "支付宝渠道不支持支付方式: {}", payment.getMethod());
        }
        return payMethod;
    }
}
