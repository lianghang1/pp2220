package io.github.lianghang1.paygateway;

import io.github.lianghang1.paygateway.api.PaymentQueryRequest;
import io.github.lianghang1.paygateway.api.PaymentRequest;
import io.github.lianghang1.paygateway.api.PaymentResult;
import io.github.lianghang1.paygateway.event.PaymentEvent;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PayPayloadType;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.support.GatewayTestHarness;
import io.github.lianghang1.paygateway.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付主流程测试：下单、幂等、重试语义、查单补单、关单、事件发布。
 *
 * @author lianghang
 */
class PaymentFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    private PaymentRequest request(String bizOrderNo) {
        return new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(10000L)
                .setSubject("测试商品")
                .setNotifyUrl("http://127.0.0.1/business/notify");
    }

    @Test
    void createPaymentShouldReturnPayloadAndPayingState() {
        PaymentResult result = harness.gateway.createPayment(request("NO-1"));

        assertThat(result.getState()).isEqualTo(PaymentState.PAYING);
        assertThat(result.getPaymentId()).isNotBlank();
        assertThat(result.getChannelOrderNo()).isEqualTo("CH-NO-1");
        assertThat(result.getPayPayload()).isNotNull();
        assertThat(result.getPayPayload().getType()).isEqualTo(PayPayloadType.QRCODE);

        Payment stored = harness.paymentStore.findById(result.getPaymentId());
        assertThat(stored.getState()).isEqualTo(PaymentState.PAYING);
        assertThat(stored.getPayPayload()).contains("test-qr://");
        assertThat(stored.getCurrency()).isEqualTo("CNY");
        assertThat(stored.getCreatedTime()).isNotNull();
    }

    @Test
    void duplicateRequestShouldBeIdempotent() {
        PaymentResult first = harness.gateway.createPayment(request("NO-2"));
        PaymentResult second = harness.gateway.createPayment(request("NO-2"));

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(harness.paymentStore.all()).hasSize(1);
    }

    @Test
    void duplicateRequestWithDifferentAmountShouldBeRejected() {
        harness.gateway.createPayment(request("NO-3"));

        PaymentRequest changed = request("NO-3").setAmount(9999L);
        assertThatThrownBy(() -> harness.gateway.createPayment(changed))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.DUPLICATE_ORDER));
    }

    @Test
    void channelFailureShouldKeepCreatedStateAndSameRequestRetries() {
        harness.channelProvider.failNextCreate.set(true);
        assertThatThrownBy(() -> harness.gateway.createPayment(request("NO-4")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CHANNEL_CALL_FAILED));

        Payment stored = harness.paymentStore.findByBizOrderNo(GatewayTestHarness.APP_ID, "NO-4");
        assertThat(stored.getState()).isEqualTo(PaymentState.CREATED);
        assertThat(stored.getErrorCode()).isEqualTo(String.valueOf(GatewayErrorCode.CHANNEL_CALL_FAILED.getCode()));

        PaymentResult retried = harness.gateway.createPayment(request("NO-4"));
        assertThat(retried.getState()).isEqualTo(PaymentState.PAYING);
        assertThat(harness.paymentStore.all()).hasSize(1);
    }

    @Test
    void unknownMethodShouldBeRejected() {
        PaymentRequest bad = request("NO-5").setMethod("NOT_REGISTERED");
        assertThatThrownBy(() -> harness.gateway.createPayment(bad))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.METHOD_NOT_SUPPORTED));
    }

    @Test
    void queryShouldReissuePayingOrderFromChannelState() {
        PaymentResult created = harness.gateway.createPayment(request("NO-6"));

        harness.channelProvider.channelPaymentStates.put(created.getPaymentId(), PaymentState.SUCCEEDED);

        PaymentResult queried = harness.gateway.queryPayment(new PaymentQueryRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizOrderNo("NO-6"));

        assertThat(queried.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(queried.getSucceedTime()).isNotNull();
        assertThat(harness.paymentStore.findById(created.getPaymentId()).getState()).isEqualTo(PaymentState.SUCCEEDED);
    }

    @Test
    void closeShouldMovePayingOrderToClosed() {
        PaymentResult created = harness.gateway.createPayment(request("NO-7"));

        PaymentResult closed = harness.gateway.closePayment(created.getPaymentId());
        assertThat(closed.getState()).isEqualTo(PaymentState.CLOSED);
        assertThat(harness.paymentStore.findById(created.getPaymentId()).getState()).isEqualTo(PaymentState.CLOSED);
    }

    @Test
    void closeSucceededOrderShouldBeRejected() {
        PaymentResult created = harness.gateway.createPayment(request("NO-8"));
        harness.channelProvider.channelPaymentStates.put(created.getPaymentId(), PaymentState.SUCCEEDED);
        harness.gateway.queryPayment(new PaymentQueryRequest().setPaymentId(created.getPaymentId()));

        assertThatThrownBy(() -> harness.gateway.closePayment(created.getPaymentId()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.STATE_ILLEGAL));
    }

    @Test
    void eventsShouldBePublishedInOrder() {
        List<PaymentEvent.EventType> received = new CopyOnWriteArrayList<PaymentEvent.EventType>();
        GatewayTestHarness listenable = new GatewayTestHarness(event -> received.add(event.getType()));

        PaymentResult created = listenable.gateway.createPayment(
                new PaymentRequest()
                        .setAppId(GatewayTestHarness.APP_ID)
                        .setBizOrderNo("NO-EVENT")
                        .setMethod(TestChannelProvider.METHOD_PAY)
                        .setAmount(5000L));
        listenable.channelProvider.channelPaymentStates.put(created.getPaymentId(), PaymentState.SUCCEEDED);
        listenable.gateway.queryPayment(new PaymentQueryRequest().setPaymentId(created.getPaymentId()));

        assertThat(received).containsExactly(
                PaymentEvent.EventType.PAYMENT_CREATED,
                PaymentEvent.EventType.PAYMENT_PAYING,
                PaymentEvent.EventType.PAYMENT_SUCCEEDED);
    }
}
