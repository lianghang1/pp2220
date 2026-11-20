package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.ProfitShareQueryRequest;
import io.github.lianghang1.conflux.pay.api.ProfitShareRequest;
import io.github.lianghang1.conflux.pay.api.ProfitShareResult;
import io.github.lianghang1.conflux.pay.api.TransferQueryRequest;
import io.github.lianghang1.conflux.pay.api.TransferRequest;
import io.github.lianghang1.conflux.pay.api.TransferResult;
import io.github.lianghang1.conflux.pay.model.ProfitShareReceiver;
import io.github.lianghang1.conflux.pay.model.ProfitShareState;
import io.github.lianghang1.conflux.pay.model.TransferState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 转账与分账流程测试：渠道归一语义（SDK 无状态）。
 *
 * @author lianghang
 */
class TransferProfitShareFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    @Test
    void createTransferShouldReturnChannelResult() {
        TransferResult result = harness.gateway.createTransfer(new TransferRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizTransferNo("TR-1")
                .setMethod(TestChannelProvider.METHOD_TRANS)
                .setPayeeAccount("account-001")
                .setPayeeName("收款人")
                .setAmount(5000L));

        assertThat(result.getState()).isEqualTo(TransferState.PROCESSING);
        assertThat(result.getBizTransferNo()).isEqualTo("TR-1");
        assertThat(result.getChannelTransferNo()).isEqualTo("CHT-TR-1");
    }

    @Test
    void queryTransferShouldReturnChannelState() {
        harness.channelProvider.channelTransferStates.put("TR-2", TransferState.SUCCEEDED);

        TransferResult result = harness.gateway.queryTransfer(new TransferQueryRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizTransferNo("TR-2")
                .setChannelCode(TestChannelProvider.CODE));

        assertThat(result.getState()).isEqualTo(TransferState.SUCCEEDED);
    }

    @Test
    void createProfitShareShouldReturnChannelResult() {
        ProfitShareResult result = harness.gateway.createProfitShare(new ProfitShareRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizShareNo("PS-1")
                .setChannelCode(TestChannelProvider.CODE)
                .setBizOrderNo("ORDER-1")
                .setChannelOrderNo("CH-ORDER-1")
                .setReceivers(Collections.singletonList(new ProfitShareReceiver()
                        .setReceiverAccount("acc-1").setAmount(1000L))));

        assertThat(result.getState()).isEqualTo(ProfitShareState.FINISHED);
        assertThat(result.getChannelShareNo()).isEqualTo("CHP-PS-1");
    }

    @Test
    void queryProfitShareShouldReturnChannelState() {
        harness.channelProvider.channelShareStates.put("PS-2", ProfitShareState.FINISHED);

        ProfitShareResult result = harness.gateway.queryProfitShare(new ProfitShareQueryRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizShareNo("PS-2")
                .setChannelCode(TestChannelProvider.CODE));

        assertThat(result.getState()).isEqualTo(ProfitShareState.FINISHED);
    }
}
