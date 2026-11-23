package com.sfi.conflux.sms.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.sms.api.SmsSendRequest;
import com.sfi.conflux.sms.api.SmsSendResult;
import com.sfi.conflux.sms.channel.SmsChannelProvider;
import com.sfi.conflux.sms.config.SmsChannelConfig;
import com.sfi.conflux.sms.config.SmsConfigProvider;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.api.universal.SupplierConfig;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
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
 * 网关门面测试：注入配置源后，send 经「configProvider → 装载 Factory → 取 blend →
 * 调用 sms4j 发送 → 归一 SmsResponse」链路。Factory 的 createSms 返回预设 mock blend，
 * 走真实装载链路但不触发任何网络。
 *
 * @author lianghang
 */
class SmsGatewayTest {

    /** 测试用 Config：supplier = mockgw，使 SmsFactory 从 ProviderFactoryHolder 取到我们注册的 mock factory。 */
    public static class MockGwConfig extends BaseConfig {
        @Override
        public String getSupplier() {
            return "mockgw";
        }
    }

    private SmsBlend mockBlend(String configId, boolean success, Object data) {
        SmsBlend blend = mock(SmsBlend.class);
        when(blend.getConfigId()).thenReturn(configId);
        SmsResponse resp = new SmsResponse();
        resp.setSuccess(success);
        resp.setData(data);
        resp.setConfigId(configId);
        when(blend.sendMessage(any(String.class), any(String.class))).thenReturn(resp);
        when(blend.sendMessage(any(String.class), any(LinkedHashMap.class))).thenReturn(resp);
        return blend;
    }

    /** 构造注入了必填配置源的网关：自定义 Factory 的 createSms 直接返回 mock blend。 */
    private SmsGateway gatewayWith(String channel, SmsBlend blend) {
        BaseProviderFactory factory = new BaseProviderFactory() {
            @Override
            public SmsBlend createSms(SupplierConfig config) {
                return blend;
            }
            @Override
            public Class getConfigClass() {
                return MockGwConfig.class;
            }
            @Override
            public String getSupplier() {
                return channel;
            }
        };
        SmsConfigProvider cp = (configId, ch) ->
                new SmsChannelConfig().setConfigId(configId).setChannelCode(ch);
        return SmsGateway.builder()
                .configProvider(cp)
                .channel(SmsChannelProvider.of(factory))
                .build();
    }

    @Test
    void shouldNormalizeSmsResponse() {
        SmsGateway gateway = gatewayWith("mockgw", mockBlend("gw::mockgw", true, "REQ-OK"));

        SmsSendRequest request = new SmsSendRequest()
                .setBizId("BIZ-1")
                .setPhone("18888888888")
                .setContent("验证码 123456");
        SmsSendResult result = gateway.send("gw", "mockgw", request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBizId()).isEqualTo("BIZ-1");
        assertThat(result.getChannel()).isEqualTo("mockgw");
        assertThat(result.getConfigId()).isEqualTo("gw");
        assertThat(result.getRaw()).isEqualTo("REQ-OK");
        assertThat(result.getError()).isNull();
    }

    @Test
    void templateParamsShouldUseMapOverload() {
        SmsBlend blend = mockBlend("tpl::mockgw", true, "OK");
        SmsGateway gateway = gatewayWith("mockgw", blend);

        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("code", "123456");
        SmsSendRequest request = new SmsSendRequest()
                .setPhone("18888888888")
                .setParams(vars);
        gateway.send("tpl", "mockgw", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(blend).sendMessage(eq("18888888888"), captor.capture());
        assertThat(captor.getValue()).containsEntry("code", "123456");
    }

    @Test
    void failureResponseShouldBeNormalized() {
        SmsGateway gateway = gatewayWith("mockgw", mockBlend("fail::mockgw", false, "BALANCE_NOT_ENOUGH"));

        SmsSendResult result = gateway.send("fail", "mockgw",
                new SmsSendRequest().setPhone("18888888888").setContent("hi"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("BALANCE_NOT_ENOUGH");
    }

    @Test
    void missingConfigShouldFailFast() {
        // 配置源返回 null → ensureLoaded 抛 CONFIG_MISSING，与支付/发票 build 必填语义对齐
        SmsGateway gateway = SmsGateway.builder().configProvider((configId, ch) -> null).build();
        assertThatThrownBy(() -> gateway.send("none", "alibaba",
                new SmsSendRequest().setPhone("18888888888").setContent("hi")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4103));
    }

    @Test
    void missingConfigProviderShouldFailFast() {
        // 必填契约：未注入 configProvider 时 build 即失败（对齐支付/发票）
        assertThatThrownBy(() -> SmsGateway.builder().build())
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4103));
    }

    @Test
    void badRequestShouldFailFast() {
        SmsGateway gateway = SmsGateway.builder()
                .configProvider((configId, ch) -> new SmsChannelConfig().setConfigId(configId).setChannelCode(ch))
                .build();
        assertThatThrownBy(() -> gateway.send("", "alibaba",
                new SmsSendRequest().setPhone("18888888888")))
                .isInstanceOf(GatewayException.class);
    }
}
