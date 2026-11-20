package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.BizOrderType;
import io.github.lianghang1.conflux.pay.support.GatewayTestHarness;
import io.github.lianghang1.conflux.pay.support.TestChannelProvider;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 渠道回调解析测试：验签与归一语义（SDK 无状态，不查订单、不推进状态）。
 *
 * @author lianghang
 */
class CallbackFlowTest {

    private final GatewayTestHarness harness = new GatewayTestHarness();

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
    void callbackShouldReturnNormalizedEvent() {
        CallbackEvent event = harness.gateway.parseCallback("app1",
                callbackRequest("CB-1", "CH-CB-1", true, 8800L));

        assertThat(event.getType()).isEqualTo(BizOrderType.PAYMENT);
        assertThat(event.getBizOrderNo()).isEqualTo("CB-1");
        assertThat(event.getChannelOrderNo()).isEqualTo("CH-CB-1");
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getAmount()).isEqualTo(8800L);
    }

    @Test
    void invalidSignatureShouldBeRejected() {
        ChannelCallbackRequest tampered = callbackRequest("CB-3", "CH-CB-3", true, 8800L)
                .setHeaders(new LinkedHashMap<String, String>());
        assertThatThrownBy(() -> harness.gateway.parseCallback("app1", tampered))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.SIGN_INVALID));
    }

    @Test
    void missingChannelCodeShouldBeRejected() {
        assertThatThrownBy(() -> harness.gateway.parseCallback("app1", new ChannelCallbackRequest()))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void repliesShouldComeFromChannelHandler() {
        assertThat(harness.gateway.callbackSuccessReply(TestChannelProvider.CODE)).isEqualTo("SUCCESS");
        assertThat(harness.gateway.callbackFailureReply(TestChannelProvider.CODE)).isEqualTo("FAIL");
    }
}
