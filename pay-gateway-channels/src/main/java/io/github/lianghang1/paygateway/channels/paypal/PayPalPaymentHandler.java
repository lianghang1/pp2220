package io.github.lianghang1.paygateway.channels.paypal;

import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPal 支付处理器：Orders v2（下单 → 用户批准 → 捕获）。
 *
 * <p>下单返回 approve 链接（REDIRECT）；订单批准后由使用方查询触发
 * 自动捕获（capture），捕获完成返回 capture_id（退款凭据）。
 * purchase_units 的 {@code custom_id} 承载网关支付单号，Webhook 回调据此定位。</p>
 *
 * @author lianghang
 */
public class PayPalPaymentHandler implements PaymentHandler {

    private final PayPalClient client;

    public PayPalPaymentHandler(PayPalClient client) {
        this.client = client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        Map<String, Object> order = new HashMap<String, Object>();
        order.put("intent", "CAPTURE");
        Map<String, Object> unit = new HashMap<String, Object>();
        unit.put("reference_id", payment.getPaymentId());
        unit.put("custom_id", payment.getPaymentId());
        Map<String, Object> amount = new HashMap<String, Object>();
        amount.put("currency_code", payment.getCurrency());
        amount.put("value", PayPalTranslator.toYuan(payment.getAmount()));
        unit.put("amount", amount);
        unit.put("description", StringUtils.defaultIfBlank(payment.getSubject(), "pay-gateway"));
        List<Map<String, Object>> units = new ArrayList<Map<String, Object>>();
        units.add(unit);
        order.put("purchase_units", units);

        Map<String, Object> result = client.execute("下单", config, "POST", "/v2/checkout/orders", order);
        String orderId = PayPalTranslator.text(result.get("id"));
        String approveUrl = PayPalTranslator.link(result, "approve");
        return new PaymentInvocation()
                .setChannelOrderNo(orderId)
                .setPayload(PayPayload.redirect(approveUrl))
                .setState(PaymentState.PAYING);
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
            return captureSnapshot(payment, config, orderNo, "COMPLETED");
        }
        if ("APPROVED".equals(status)) {
            // 用户已批准，主动捕获并返回终态快照
            Map<String, Object> captured = client.execute("捕获", config, "POST",
                    "/v2/checkout/orders/" + orderNo + "/capture", null);
            String captureStatus = PayPalTranslator.text(captured.get("status"));
            if ("COMPLETED".equals(captureStatus)) {
                return captureSnapshot(payment, config, orderNo, captureStatus);
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
    private PaymentSnapshot captureSnapshot(Payment payment, ChannelConfig config, String orderNo, String status) {
        Map<String, Object> order = client.execute("查单", config, "GET", "/v2/checkout/orders/" + orderNo, null);
        String captureId = PayPalTranslator.captureId(order);
        Long paidAmount = PayPalTranslator.captureAmount(order);
        PaymentSnapshot snapshot = new PaymentSnapshot()
                .setChannelOrderNo(captureId != null ? captureId : orderNo)
                .setState(PaymentState.SUCCEEDED)
                .setPaidAmount(paidAmount != null ? paidAmount : payment.getAmount())
                .setSucceedTime(java.time.LocalDateTime.now())
                .setRawMsg(status);
        return snapshot;
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "PayPal 订单无独立关单接口，请以查单捕获流程为准");
    }
}
