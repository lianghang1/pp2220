package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关门面测试：把 mock SmsBlend 注册进 sms4j 的 SmsFactory，验证 send
 * 「取 blend → 调用 sms4j 发送 → 归一 SmsResponse」链路。不触发真实网络。
 *
 * @author lianghang
 */
class SmsGatewayTest {

    private SmsBlend mockBlend(String configId, boolean success, Object data) {
        SmsBlend blend = mock(SmsBlend.class);
        when(blend.getConfigId()).thenReturn(configId);
        SmsResponse resp = new SmsResponse();
        resp.setSuccess(success);
        resp.setData(data);
        resp.setConfigId(configId);
        when(blend.sendMessage(any(String.class), any(String.class))).thenReturn(resp);
        when(blend.sendMessage(any(String.class), any(LinkedHashMap.class))).thenReturn(resp);
        SmsFactory.register(blend);
        return blend;
    }

    @Test
    void shouldNormalizeSmsResponse() {
        SmsGateway gateway = SmsGateway.builder().build();
        mockBlend("gw::alibaba", true, "REQ-OK");

        SmsSendRequest request = new SmsSendRequest()
                .setBizId("BIZ-1")
                .setPhone("18888888888")
                .setContent("验证码 123456");
        SmsSendResult result = gateway.send("gw", "alibaba", request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBizId()).isEqualTo("BIZ-1");
        assertThat(result.getChannel()).isEqualTo("alibaba");
        assertThat(result.getAppId()).isEqualTo("gw");
        assertThat(result.getRaw()).isEqualTo("REQ-OK");
        assertThat(result.getError()).isNull();
    }

    @Test
    void templateParamsShouldUseMapOverload() {
        SmsGateway gateway = SmsGateway.builder().build();
        SmsBlend blend = mockBlend("tpl::alibaba", true, "OK");

        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("code", "123456");
        SmsSendRequest request = new SmsSendRequest()
                .setPhone("18888888888")
                .setParams(vars);
        gateway.send("tpl", "alibaba", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(blend).sendMessage(eq("18888888888"), captor.capture());
        assertThat(captor.getValue()).containsEntry("code", "123456");
    }

    @Test
    void failureResponseShouldBeNormalized() {
        SmsGateway gateway = SmsGateway.builder().build();
        mockBlend("fail::alibaba", false, "BALANCE_NOT_ENOUGH");

        SmsSendResult result = gateway.send("fail", "alibaba",
                new SmsSendRequest().setPhone("18888888888").setContent("hi"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("BALANCE_NOT_ENOUGH");
    }

    @Test
    void missingBlendShouldFailFast() {
        SmsGateway gateway = SmsGateway.builder().build();
        assertThatThrownBy(() -> gateway.send("none", "alibaba",
                new SmsSendRequest().setPhone("18888888888").setContent("hi")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4103));
    }

    @Test
    void badRequestShouldFailFast() {
        SmsGateway gateway = SmsGateway.builder().build();
        assertThatThrownBy(() -> gateway.send("", "alibaba",
                new SmsSendRequest().setPhone("18888888888")))
                .isInstanceOf(GatewayException.class);
    }
}
