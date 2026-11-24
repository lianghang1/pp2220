package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
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
class NotificationGatewayTest {

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
    private NotificationGateway gatewayWith(String channel, SmsBlend blend) {
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
        NotificationConfigProvider cp = (configId, ch) ->
                new NotificationConfig().setConfigId(configId).setChannelCode(ch);
        return NotificationGateway.builder()
                .configProvider(cp)
                .channel(SmsChannelProvider.of(factory))
                .build();
    }

    @Test
    void shouldNormalizeSmsResponse() {
        NotificationGateway gateway = gatewayWith("mockgw", mockBlend("gw::mockgw", true, "REQ-OK"));

        NotificationSendRequest request = new NotificationSendRequest()
                .setChannelCode("mockgw")
                .setBizId("BIZ-1")
                .setPhone("18888888888")
                .setContent("验证码 123456");
        NotificationSendResult result = gateway.send("gw", request);

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
        NotificationGateway gateway = gatewayWith("mockgw", blend);

        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put("code", "123456");
        NotificationSendRequest request = new NotificationSendRequest()
                .setChannelCode("mockgw")
                .setPhone("18888888888")
                .setParams(vars);
        gateway.send("tpl", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(blend).sendMessage(eq("18888888888"), captor.capture());
        assertThat(captor.getValue()).containsEntry("code", "123456");
    }

    @Test
    void failureResponseShouldBeNormalized() {
        NotificationGateway gateway = gatewayWith("mockgw", mockBlend("fail::mockgw", false, "BALANCE_NOT_ENOUGH"));

        NotificationSendResult result = gateway.send("fail",
                new NotificationSendRequest().setChannelCode("mockgw").setPhone("18888888888").setContent("hi"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("BALANCE_NOT_ENOUGH");
    }

    @Test
    void missingConfigShouldFailFast() {
        // 配置源返回 null → ensureLoaded 抛 CONFIG_MISSING，与支付/发票 build 必填语义对齐
        NotificationGateway gateway = NotificationGateway.builder().configProvider((configId, ch) -> null).build();
        assertThatThrownBy(() -> gateway.send("none",
                new NotificationSendRequest().setChannelCode("alibaba").setPhone("18888888888").setContent("hi")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4103));
    }

    @Test
    void missingConfigProviderShouldFailFast() {
        // 必填契约：未注入 configProvider 时 build 即失败（对齐支付/发票）
        assertThatThrownBy(() -> NotificationGateway.builder().build())
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4103));
    }

    @Test
    void badRequestShouldFailFast() {
        NotificationGateway gateway = NotificationGateway.builder()
                .configProvider((configId, ch) -> new NotificationConfig().setConfigId(configId).setChannelCode(ch))
                .build();
        assertThatThrownBy(() -> gateway.send("",
                new NotificationSendRequest().setChannelCode("alibaba").setPhone("18888888888")))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void missingPhoneShouldFailFastInChannel() {
        // phone 校验下沉到短信渠道：门面只守 configId/message/channelCode，phone 由 Sms4jChannel 校验
        NotificationGateway gateway = gatewayWith("mockgw", mockBlend("p::mockgw", true, "OK"));
        assertThatThrownBy(() -> gateway.send("p",
                new NotificationSendRequest().setChannelCode("mockgw").setContent("hi")))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(4106));
    }
}
