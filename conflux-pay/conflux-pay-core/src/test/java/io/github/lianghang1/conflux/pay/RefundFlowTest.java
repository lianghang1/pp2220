package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.lianghang1.conflux.common.exception.GatewayException;

/**
 * 退款流程测试：发起退款、退款查单的渠道归一语义（SDK 无状态，可退余额由使用方校验）。
 *
 * @author lianghang
 */
class RefundFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

    private RefundRequest request(String bizRefundNo) {
        return new RefundRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizRefundNo(bizRefundNo)
                .setChannelCode(TestChannelProvider.CODE)
                .setBizOrderNo("ORDER-" + bizRefundNo)
                .setTotalAmount(10000L)
                .setAmount(3000L)
                .setReason("测试退款");
    }

    @Test
    void createRefundShouldReturnChannelResult() {
        RefundResult result = harness.gateway.createRefund(request("RF-1"));

        assertThat(result.getState()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(result.getBizRefundNo()).isEqualTo("RF-1");
        assertThat(result.getChannelRefundNo()).isEqualTo("CHR-RF-1");
    }

    @Test
    void missingTotalAmountShouldBeRejected() {
        assertThatThrownBy(() -> harness.gateway.createRefund(request("RF-BAD").setTotalAmount(null)))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void queryRefundShouldReturnChannelState() {
        harness.channelProvider.channelRefundStates.put("RF-2", RefundState.SUCCEEDED);

        RefundResult result = harness.gateway.queryRefund(new RefundQueryRequest()
                .setConfigId(GatewayTestHarness.APP_ID)
                .setBizRefundNo("RF-2")
                .setChannelCode(TestChannelProvider.CODE));

        assertThat(result.getState()).isEqualTo(RefundState.SUCCEEDED);
        assertThat(result.getSucceedTime()).isNotNull();
    }
}
