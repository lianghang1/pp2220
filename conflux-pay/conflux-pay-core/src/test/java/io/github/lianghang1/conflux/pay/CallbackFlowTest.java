package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.ChannelCallbackReply;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 渠道回调流程测试：验签、状态迁移、幂等应答、金额一致性、下游通知入队。
 *
 * @author lianghang
 */
class CallbackFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    private PaymentResult createPaying(String bizOrderNo) {
        return harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(8800L)
                .setNotifyUrl("http://127.0.0.1/business/notify"));
    }

    private ChannelCallbackRequest callbackRequest(String bizOrderNo, String channelOrderNo,
                                                   boolean success, Long amount) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("type", "PAYMENT");
        body.put("bizOrderNo", bizOrderNo);
        body.put("channelOrderNo", channelOrderNo);
        body.put("success", success);
        body.put("amount", amount);
        body.put("occurTime", "2026-11-14 12:00:00");
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Test-Sign", TestChannelProvider.VALID_SIGN);
        return new ChannelCallbackRequest()
                .setChannelCode(TestChannelProvider.CODE)
                .setHeaders(headers)
                .setBody(JsonUtils.toJson(body));
    }

    @Test
    void successCallbackShouldMarkPaymentSucceededAndEnqueueNotify() {
        createPaying("CB-1");

        ChannelCallbackReply reply = harness.gateway.handleCallback(
                callbackRequest("CB-1", "CH-CB-1", true, 8800L));

        assertThat(reply.getStatusCode()).isEqualTo(200);
        assertThat(reply.getBody()).isEqualTo("SUCCESS");
        assertThat(harness.paymentStore.findByBizOrderNo(GatewayTestHarness.APP_ID, "CB-1").getState())
                .isEqualTo(PaymentState.SUCCEEDED);

        assertThat(harness.notifier.submitted).hasSize(1);
        NotifyLog log = harness.notifier.submitted.get(0);
        assertThat(log.getOrderId()).isNotBlank();
        assertThat(log.getRequestContent()).contains("PAYMENT").contains("SUCCEEDED").contains("8800");
        assertThat(harness.notifyLogStore.all()).hasSize(1);
    }

    @Test
    void duplicateCallbackShouldBeIdempotentWithoutDoubleNotify() {
        createPaying("CB-2");

        harness.gateway.handleCallback(callbackRequest("CB-2", "CH-CB-2", true, 8800L));
        harness.gateway.handleCallback(callbackRequest("CB-2", "CH-CB-2", true, 8800L));

        assertThat(harness.notifier.submitted).hasSize(1);
        assertThat(harness.paymentStore.findByBizOrderNo(GatewayTestHarness.APP_ID, "CB-2").getState())
                .isEqualTo(PaymentState.SUCCEEDED);
    }

    @Test
    void invalidSignatureShouldBeRejected() {
        createPaying("CB-3");

        ChannelCallbackRequest tampered = callbackRequest("CB-3", "CH-CB-3", true, 8800L)
                .setHeaders(new LinkedHashMap<String, String>());
        assertThatThrownBy(() -> harness.gateway.handleCallback(tampered))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.SIGN_INVALID));
        assertThat(harness.paymentStore.findByBizOrderNo(GatewayTestHarness.APP_ID, "CB-3").getState())
                .isEqualTo(PaymentState.PAYING);
    }

    @Test
    void amountMismatchShouldBeRejected() {
        createPaying("CB-4");

        assertThatThrownBy(() -> harness.gateway.handleCallback(
                callbackRequest("CB-4", "CH-CB-4", true, 1L)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.AMOUNT_MISMATCH));
    }

    @Test
    void unknownOrderShouldBeRejected() {
        assertThatThrownBy(() -> harness.gateway.handleCallback(
                callbackRequest("NO-SUCH", "CH-NONE", true, 100L)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void failedCallbackShouldMarkPaymentFailed() {
        createPaying("CB-5");

        harness.gateway.handleCallback(callbackRequest("CB-5", "CH-CB-5", false, 8800L));

        assertThat(harness.paymentStore.findByBizOrderNo(GatewayTestHarness.APP_ID, "CB-5").getState())
                .isEqualTo(PaymentState.FAILED);
    }
}
