package io.github.lianghang1.paygateway.channels.paypal;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channels.paypal.paymethod.PayPalPayMethod;
import io.github.lianghang1.paygateway.channels.paypal.paymethod.PpPcPayMethod;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * PayPal 支付处理器门面：按支付方式路由到独立的 PayMethod 实现
 * （PpPc：Orders v2 下单），查单含自动捕获（APPROVED → capture → COMPLETED）。
 *
 * <p>捕获完成后 channelOrderNo 更新为 capture_id（退款凭据）；
 * purchase_units 的 custom_id 承载网关支付单号供 Webhook 定位。</p>
 *
 * @author lianghang
 */
public class PayPalPaymentHandler implements PaymentHandler {

    private final PayPalClient client = new PayPalClient();
    private final Map<String, PayPalPayMethod> payMethods = new HashMap<String, PayPalPayMethod>();

    public PayPalPaymentHandler() {
        register(new PpPcPayMethod());
    }

    private void register(PayPalPayMethod payMethod) {
        payMethods.put(payMethod.method(), payMethod);
    }

    /** 渠道已支持的支付方式集合（能力声明/调试用）。 */
    public Set<String> supportedMethods() {
        return payMethods.keySet();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        PayPalPayMethod payMethod = payMethodOf(payment);
        return payMethod.createPayment(payment, config, client);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        String orderNo = payment.getChannelOrderNo();
        if (StringUtils.isBlank(orderNo)) {
            return new PaymentSnapshot().setState(PaymentState.PAYING).setRawMsg("no channel order id");
        }
        Map<String, Object> order = client.execute("查单", config, "GET", "/v2/checkout/orders/" + orderNo, null);
        String status = PayPalTranslator.text(order.get("status"));
        if ("COMPLETED".equals(status)) {
            return completedSnapshot(payment, order, status);
        }
        if ("APPROVED".equals(status)) {
            // 用户已批准，主动捕获并返回终态快照
            Map<String, Object> captured = client.execute("捕获", config, "POST",
                    "/v2/checkout/orders/" + orderNo + "/capture", null);
            String captureStatus = PayPalTranslator.text(captured.get("status"));
            if ("COMPLETED".equals(captureStatus)) {
                return completedSnapshot(payment, captured, captureStatus);
            }
            return new PaymentSnapshot()
                    .setChannelOrderNo(orderNo)
                    .setState(PaymentState.PAYING)
                    .setRawMsg(captureStatus);
        }
        return new PaymentSnapshot()
                .setChannelOrderNo(orderNo)
                .setState(PayPalTranslator.toPaymentState(status))
                .setRawMsg(status);
    }

    @SuppressWarnings("unchecked")
    private PaymentSnapshot completedSnapshot(Payment payment, Map<String, Object> order, String status) {
        String captureId = PayPalTranslator.captureId(order);
        Long paidAmount = PayPalTranslator.captureAmount(order);
        return new PaymentSnapshot()
                .setChannelOrderNo(captureId != null ? captureId : payment.getChannelOrderNo())
                .setState(PaymentState.SUCCEEDED)
                .setPaidAmount(paidAmount != null ? paidAmount : payment.getAmount())
                .setSucceedTime(java.time.LocalDateTime.now())
                .setRawMsg(status);
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "PayPal 订单无独立关单接口，请以查单捕获流程为准");
    }

    private PayPalPayMethod payMethodOf(Payment payment) {
        PayPalPayMethod payMethod = payMethods.get(payment.getMethod());
        if (payMethod == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "PayPal 渠道不支持支付方式: {}", payment.getMethod());
        }
        return payMethod;
    }
}
