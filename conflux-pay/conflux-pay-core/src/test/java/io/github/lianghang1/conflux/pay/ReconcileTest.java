package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.BillEntry;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.ReconReport;
import io.github.lianghang1.conflux.pay.model.BillEntryType;
import io.github.lianghang1.conflux.pay.model.DiscrepancyType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对账引擎测试：双向比对、差错分类留痕、渠道成功而本地未成功的自动修正。
 *
 * @author lianghang
 */
class ReconcileTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();
    // 订单时间戳取当前时间，账单日期取当天以保证「渠道侧流水与本地订单同日」
    private final LocalDate billDate = LocalDate.now();

    private String createSucceeded(String bizOrderNo, long amount) {
        String paymentId = harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(amount)
                .setNotifyUrl("http://127.0.0.1/business/notify")).getPaymentId();
        harness.channelProvider.channelPaymentStates.put(paymentId, PaymentState.SUCCEEDED);
        harness.gateway.queryPayment(new io.github.lianghang1.conflux.pay.api.PaymentQueryRequest()
                .setPaymentId(paymentId));
        return paymentId;
    }

    private String createPaying(String bizOrderNo, long amount) {
        return harness.gateway.createPayment(new PaymentRequest()
                .setAppId(GatewayTestHarness.APP_ID)
                .setBizOrderNo(bizOrderNo)
                .setMethod(TestChannelProvider.METHOD_PAY)
                .setAmount(amount)
                .setNotifyUrl("http://127.0.0.1/business/notify")).getPaymentId();
    }

    private BillEntry payEntry(String bizOrderNo, long amount, boolean success) {
        return new BillEntry()
                .setEntryType(BillEntryType.PAYMENT)
                .setChannelOrderNo("CH-" + bizOrderNo)
                .setBizOrderNo(bizOrderNo)
                .setAmount(amount)
                .setSuccess(success)
                .setRawState(success ? "SUCCESS" : "FAIL")
                .setOccurTime(billDate.atTime(10, 0));
    }

    @Test
    void matchedOrdersShouldProduceCleanReport() {
        createSucceeded("RC-1", 1000L);
        harness.channelProvider.billEntries.addAll(Arrays.asList(payEntry("RC-1", 1000L, true)));

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getMatchedCount()).isEqualTo(1);
        assertThat(report.getDiscrepancies()).isEmpty();
        assertThat(harness.reconDiscrepancyStore.all()).isEmpty();
    }

    @Test
    void channelSuccessLocalPayingShouldBeAutoFixed() {
        String paymentId = createPaying("RC-2", 2000L);
        harness.channelProvider.billEntries.addAll(Arrays.asList(payEntry("RC-2", 2000L, true)));

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getDiscrepancies()).hasSize(1);
        assertThat(report.getDiscrepancies().get(0).getDiscrepancyType()).isEqualTo(DiscrepancyType.STATE_MISMATCH);
        assertThat(harness.paymentStore.findById(paymentId).getState()).isEqualTo(PaymentState.SUCCEEDED);
        // 自动修正会触发下游通知入队
        assertThat(harness.notifier.submitted).hasSize(1);
        assertThat(harness.reconDiscrepancyStore.all()).hasSize(1);
    }

    @Test
    void localSuccessWithoutChannelEntryShouldBeChannelMissing() {
        createSucceeded("RC-3", 3000L);

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getDiscrepancies()).hasSize(1);
        assertThat(report.getDiscrepancies().get(0).getDiscrepancyType()).isEqualTo(DiscrepancyType.CHANNEL_MISSING);
        assertThat(report.getDiscrepancies().get(0).getLocalAmount()).isEqualTo(3000L);
    }

    @Test
    void channelEntryWithoutLocalOrderShouldBeLocalMissing() {
        harness.channelProvider.billEntries.addAll(Arrays.asList(payEntry("RC-UNKNOWN", 4000L, true)));

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getDiscrepancies()).hasSize(1);
        assertThat(report.getDiscrepancies().get(0).getDiscrepancyType()).isEqualTo(DiscrepancyType.LOCAL_MISSING);
        assertThat(report.getDiscrepancies().get(0).getChannelAmount()).isEqualTo(4000L);
    }

    @Test
    void amountMismatchShouldBeRecorded() {
        createSucceeded("RC-5", 5000L);
        harness.channelProvider.billEntries.addAll(Arrays.asList(payEntry("RC-5", 4999L, true)));

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getDiscrepancies()).hasSize(1);
        assertThat(report.getDiscrepancies().get(0).getDiscrepancyType()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
    }

    @Test
    void unpaidLocalOrderAndFailedChannelEntryShouldBeConsistent() {
        createPaying("RC-6", 6000L);
        harness.channelProvider.billEntries.addAll(Arrays.asList(payEntry("RC-6", 6000L, false)));

        ReconReport report = harness.gateway.reconcile(
                TestChannelProvider.CODE, GatewayTestHarness.APP_ID, billDate);

        assertThat(report.getDiscrepancies()).isEmpty();
        assertThat(report.getMatchedCount()).isEqualTo(1);
    }
}
