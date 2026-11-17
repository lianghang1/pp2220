package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 退款可退余额回归：在途退款必须占用额度，失败/关闭退款不占用。
 *
 * @author lianghang
 */
class RefundBalanceTest {

    private String succeededPayment(GatewayTestHarness h, String bizNo, long amount) {
        PaymentResult created = h.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizOrderNo(bizNo)
                .setMethod(TestChannelProvider.METHOD_PAY).setAmount(amount));
        h.channelProvider.channelPaymentStates.put(created.getPaymentId(), PaymentState.SUCCEEDED);
        h.gateway.queryPayment(new PaymentQueryRequest().setPaymentId(created.getPaymentId()));
        return created.getPaymentId();
    }

    private void saveRefund(GatewayTestHarness h, String refundId, String bizRefundNo,
                            String paymentId, long amount, RefundState state) {
        LocalDateTime now = LocalDateTime.now();
        h.refundStore.save(new Refund()
                .setRefundId(refundId).setAppId(GatewayTestHarness.APP_ID)
                .setBizRefundNo(bizRefundNo).setPaymentId(paymentId)
                .setAmount(amount).setState(state)
                .setCreatedTime(now).setUpdatedTime(now));
    }

    /** Bug C：存在占满全额的在途退款时，第二笔全额退款应被拒，不可超退。 */
    @Test
    void inFlightRefundReservesBalance() {
        GatewayTestHarness h = new GatewayTestHarness();
        String paymentId = succeededPayment(h, "RB-PAY", 10000L);
        saveRefund(h, "RF-PROCESSING", "RF-INFLIGHT", paymentId, 10000L, RefundState.PROCESSING);

        GatewayException ex = assertThrows(GatewayException.class, () ->
                h.gateway.createRefund(new RefundRequest()
                        .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-NEW")
                        .setPaymentId(paymentId).setAmount(10000L).setReason("超退测试")));
        assertEquals(GatewayErrorCode.REFUND_EXCEED_REFUNDABLE, ex.getErrorCode());
    }

    /** 创建态退款同样占用额度。 */
    @Test
    void createdRefundReservesBalance() {
        GatewayTestHarness h = new GatewayTestHarness();
        String paymentId = succeededPayment(h, "RB-PAY2", 10000L);
        saveRefund(h, "RF-CREATED", "RF-CREATED-BIZ", paymentId, 6000L, RefundState.CREATED);

        GatewayException ex = assertThrows(GatewayException.class, () ->
                h.gateway.createRefund(new RefundRequest()
                        .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-NEW2")
                        .setPaymentId(paymentId).setAmount(5000L).setReason("超退测试")));
        assertEquals(GatewayErrorCode.REFUND_EXCEED_REFUNDABLE, ex.getErrorCode());
    }

    /** 失败退款不占用额度，剩余部分仍可退。 */
    @Test
    void failedRefundDoesNotReserveBalance() {
        GatewayTestHarness h = new GatewayTestHarness();
        String paymentId = succeededPayment(h, "RB-PAY3", 10000L);
        saveRefund(h, "RF-FAILED", "RF-FAILED-BIZ", paymentId, 10000L, RefundState.FAILED);

        RefundResult result = h.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-NEW3")
                .setPaymentId(paymentId).setAmount(10000L).setReason("失败退款不占额度"));
        assertEquals(10000L, result.getAmount());
    }
}
