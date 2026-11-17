package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 退款流程测试：可退余额校验、部分/全额退款联动、非法状态拦截。
 *
 * @author lianghang
 */
class RefundFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();
    private String paymentId;

    @BeforeEach
    void createSucceededPayment() {
        PaymentResult created = harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo("RF-PAY")
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(10000L));
        harness.channelProvider.channelPaymentStates.put(created.getPaymentId(), PaymentState.SUCCEEDED);
        harness.gateway.queryPayment(new io.github.lianghang1.conflux.pay.api.PaymentQueryRequest()
                .setPaymentId(created.getPaymentId()));
        this.paymentId = created.getPaymentId();
    }

    @Test
    void partialRefundShouldMovePaymentToPartRefunded() {
        RefundResult refund = harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizRefundNo("RF-1")
                .setPaymentId(paymentId)
                .setAmount(3000L)
                .setReason("部分退款"));

        assertThat(refund.getState()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(refund.getChannelRefundNo()).isEqualTo("CHR-RF-1");

        Payment payment = harness.paymentStore.findById(paymentId);
        assertThat(payment.getState()).isEqualTo(PaymentState.PART_REFUNDED);
    }

    @Test
    void cumulativeFullRefundShouldMovePaymentToRefunded() {
        harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-A")
                .setPaymentId(paymentId).setAmount(6000L));
        harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-B")
                .setPaymentId(paymentId).setAmount(4000L));

        assertThat(harness.paymentStore.findById(paymentId).getState()).isEqualTo(PaymentState.REFUNDED);
    }

    @Test
    void refundExceedingRefundableShouldBeRejected() {
        harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-C")
                .setPaymentId(paymentId).setAmount(7000L));

        assertThatThrownBy(() -> harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-D")
                .setPaymentId(paymentId).setAmount(4000L)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.REFUND_EXCEED_REFUNDABLE));
    }

    @Test
    void refundOnUnpaidOrderShouldBeRejected() {
        PaymentResult created = harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo("RF-PAY-UNPAID")
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(5000L));

        assertThatThrownBy(() -> harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-F")
                .setPaymentId(created.getPaymentId()).setAmount(1000L)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.STATE_ILLEGAL));
    }

    @Test
    void refundQueryShouldReturnLocalState() {
        RefundResult refund = harness.gateway.createRefund(new RefundRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-G")
                .setPaymentId(paymentId).setAmount(2000L));

        RefundResult queried = harness.gateway.queryRefund(new RefundQueryRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizRefundNo("RF-G"));
        assertThat(queried.getState()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(queried.getRefundId()).isEqualTo(refund.getRefundId());
    }
}
