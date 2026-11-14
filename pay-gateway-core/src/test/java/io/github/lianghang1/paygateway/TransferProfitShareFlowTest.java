package io.github.lianghang1.paygateway;

import io.github.lianghang1.paygateway.api.PaymentRequest;
import io.github.lianghang1.paygateway.api.ProfitShareQueryRequest;
import io.github.lianghang1.paygateway.api.ProfitShareRequest;
import io.github.lianghang1.paygateway.api.ProfitShareResult;
import io.github.lianghang1.paygateway.api.TransferQueryRequest;
import io.github.lianghang1.paygateway.api.TransferRequest;
import io.github.lianghang1.paygateway.api.TransferResult;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.ProfitShareReceiver;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import io.github.lianghang1.paygateway.model.TransferState;
import io.github.lianghang1.paygateway.support.GatewayTestHarness;
import io.github.lianghang1.paygateway.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 转账与分账流程测试。
 *
 * @author lianghang
 */
class TransferProfitShareFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    private String createSucceededPayment(String bizOrderNo, long amount) {
        String paymentId = harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(amount)).getPaymentId();
        harness.channelProvider.channelPaymentStates.put(paymentId, PaymentState.SUCCEEDED);
        harness.gateway.queryPayment(new io.github.lianghang1.paygateway.api.PaymentQueryRequest()
                .setPaymentId(paymentId));
        return paymentId;
    }

    @Test
    void createTransferShouldReturnProcessingAndCallbackCompletesIt() {
        TransferResult created = harness.gateway.createTransfer(new TransferRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizTransferNo("TR-1")
                .setMethod(TestChannelProvider.METHOD_TRANS)
                .setPayeeAccount("payee-openid-001")
                .setPayeeName("张三")
                .setAmount(5000L)
                .setRemark("佣金"));

        assertThat(created.getState()).isEqualTo(TransferState.PROCESSING);
        assertThat(created.getChannelTransferNo()).isEqualTo("CHT-TR-1");

        // 模拟渠道侧转账完成 + 回调
        harness.channelProvider.channelTransferStates.put(
                harness.transferStore.findByBizTransferNo(GatewayTestHarness.APP_ID, "TR-1").getTransferId(),
                TransferState.SUCCEEDED);
        TransferResult queried = harness.gateway.queryTransfer(new TransferQueryRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizTransferNo("TR-1"));
        assertThat(queried.getState()).isEqualTo(TransferState.SUCCEEDED);
    }

    @Test
    void duplicateTransferRequestShouldBeIdempotent() {
        TransferRequest request = new TransferRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizTransferNo("TR-2")
                .setMethod(TestChannelProvider.METHOD_TRANS)
                .setPayeeAccount("payee-openid-002")
                .setAmount(3000L);
        TransferResult first = harness.gateway.createTransfer(request);
        TransferResult second = harness.gateway.createTransfer(request);

        assertThat(second.getTransferId()).isEqualTo(first.getTransferId());
    }

    @Test
    void transferWithoutPayeeShouldBeRejected() {
        assertThatThrownBy(() -> harness.gateway.createTransfer(new TransferRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizTransferNo("TR-3")
                .setMethod(TestChannelProvider.METHOD_TRANS)
                .setAmount(1000L)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.BAD_REQUEST));
    }

    @Test
    void profitShareShouldFinishOnSucceededPayment() {
        String paymentId = createSucceededPayment("PS-PAY", 10000L);

        ProfitShareResult result = harness.gateway.createProfitShare(new ProfitShareRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizShareNo("PS-1")
                .setPaymentId(paymentId)
                .setReceivers(Arrays.asList(
                        new ProfitShareReceiver()
                                .setReceiverType("MERCHANT")
                                .setReceiverAccount("mch-001")
                                .setAmount(1000L)
                                .setDescription("平台分佣"),
                        new ProfitShareReceiver()
                                .setReceiverType("PERSONAL")
                                .setReceiverAccount("person-001")
                                .setAmount(500L))));

        assertThat(result.getState()).isEqualTo(ProfitShareState.FINISHED);
        assertThat(result.getAmount()).isEqualTo(1500L);
        assertThat(result.getChannelShareNo()).isEqualTo("CHP-PS-1");
        assertThat(harness.profitShareStore.findByBizShareNo(GatewayTestHarness.APP_ID, "PS-1").getState())
                .isEqualTo(ProfitShareState.FINISHED);

        ProfitShareResult queried = harness.gateway.queryProfitShare(new ProfitShareQueryRequest()
                .setAppId(GatewayTestHarness.APP_ID).setBizShareNo("PS-1"));
        assertThat(queried.getState()).isEqualTo(ProfitShareState.FINISHED);
    }

    @Test
    void profitShareOnUnpaidPaymentShouldBeRejected() {
        String paymentId = harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo("PS-PAY-UNPAID")
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(10000L)).getPaymentId();

        assertThatThrownBy(() -> harness.gateway.createProfitShare(new ProfitShareRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizShareNo("PS-2")
                .setPaymentId(paymentId)
                .setReceivers(Arrays.asList(new ProfitShareReceiver()
                        .setReceiverType("MERCHANT").setReceiverAccount("mch-001").setAmount(1000L)))))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.STATE_ILLEGAL));
    }
}
