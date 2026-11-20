package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.PayPayloadType;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付流程测试：下单、查单、关单的渠道归一语义（SDK 无状态）。
 *
 * @author lianghang
 */
class PaymentFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    private PaymentRequest request(String bizOrderNo) {
        return new PaymentRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(10000L)
                .setSubject("测试商品");
    }

    private PaymentQueryRequest locate(String bizOrderNo) {
        return new PaymentQueryRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setChannelCode(TestChannelProvider.CODE);
    }

    @Test
    void createPaymentShouldReturnPayloadAndPayingState() {
        PaymentResult result = harness.gateway.createPayment(request("NO-1"));

        assertThat(result.getState()).isEqualTo(PaymentState.PAYING);
        assertThat(result.getBizOrderNo()).isEqualTo("NO-1");
        assertThat(result.getChannelOrderNo()).isEqualTo("CH-NO-1");
        assertThat(result.getPayPayload()).isNotNull();
        assertThat(result.getPayPayload().getType()).isEqualTo(PayPayloadType.QRCODE);
    }

    @Test
    void channelFailureShouldPropagate() {
        harness.channelProvider.failNextCreate.set(true);
        assertThatThrownBy(() -> harness.gateway.createPayment(request("NO-4")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CHANNEL_CALL_FAILED));
    }

    @Test
    void unknownMethodShouldBeRejected() {
        PaymentRequest bad = request("NO-5").setMethod("NOT_REGISTERED");
        assertThatThrownBy(() -> harness.gateway.createPayment(bad))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.METHOD_NOT_SUPPORTED));
    }

    @Test
    void missingChannelCodeShouldBeRejectedOnQuery() {
        assertThatThrownBy(() -> harness.gateway.queryPayment(
                new PaymentQueryRequest().setConfigId(GatewayTestHarness.APP_ID).setBizOrderNo("NO-X")))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void queryShouldReturnChannelState() {
        harness.gateway.createPayment(request("NO-6"));
        harness.channelProvider.channelPaymentStates.put("NO-6", PaymentState.SUCCEEDED);

        PaymentResult queried = harness.gateway.queryPayment(locate("NO-6"));

        assertThat(queried.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(queried.getSucceedTime()).isNotNull();
    }

    @Test
    void closeShouldReturnClosedState() {
        harness.gateway.createPayment(request("NO-7"));

        PaymentResult closed = harness.gateway.closePayment(locate("NO-7"));

        assertThat(closed.getState()).isEqualTo(PaymentState.CLOSED);
    }

    @Test
    void closePaidOrderShouldReturnChannelSucceededState() {
        harness.gateway.createPayment(request("NO-8"));
        harness.channelProvider.channelPaymentStates.put("NO-8", PaymentState.SUCCEEDED);

        PaymentResult result = harness.gateway.closePayment(locate("NO-8"));

        assertThat(result.getState()).isEqualTo(PaymentState.SUCCEEDED);
    }

    @Test
    void repeatedCreateWithSameBizOrderNoShouldHitChannelIdempotency() {
        // 渠道商户单号即 bizOrderNo，重复下单由渠道幂等键兜底（SDK 不做查重）
        PaymentResult first = harness.gateway.createPayment(request("NO-DUP"));
        PaymentResult second = harness.gateway.createPayment(request("NO-DUP"));

        assertThat(first.getChannelOrderNo()).isEqualTo(second.getChannelOrderNo());
    }
}
