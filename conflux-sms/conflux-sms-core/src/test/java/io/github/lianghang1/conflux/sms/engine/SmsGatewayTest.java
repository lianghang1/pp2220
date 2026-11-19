package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channel.SmsParams;
import io.github.lianghang1.conflux.sms.channel.SmsSender;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 短信网关门面测试：用桩渠道验证「纯 jar builder 组装 + 统一 send 调用」链路，
 * 不依赖网络与真实凭据。验证 SDK 无状态门面按 appId+渠道取配置、路由并归一结果。
 *
 * @author lianghang
 */
class SmsGatewayTest {

    /** 桩渠道：记录收到的配置与请求，返回固定成功结果。 */
    private static class StubProvider implements SmsChannelProvider {
        SmsSendRequest lastRequest;
        SmsChannelConfig lastConfig;

        @Override
        public String channelCode() {
            return "stub";
        }

        @Override
        public SmsParams parseParams(Map<String, String> properties) {
            return () -> "stub-app";
        }

        @Override
        public SmsSender smsSender() {
            return (request, config) -> {
                lastRequest = request;
                lastConfig = config;
                return new SmsSendResult().setSuccess(true).setRaw("stub-ok");
            };
        }
    }

    @Test
    void builderShouldRouteAndNormalizeSend() {
        StubProvider stub = new StubProvider();
        SmsConfigProvider configProvider = (appId, channel) ->
                new SmsChannelConfig().setAppId(appId).setChannelCode(channel)
                        .setParams(() -> "stub-app");

        SmsGateway gateway = SmsGateway.builder()
                .channel(stub)
                .configProvider(configProvider)
                .build();

        SmsSendRequest request = new SmsSendRequest()
                .setBizId("BIZ-1")
                .setPhone("18888888888")
                .setContent("验证码 123456");
        SmsSendResult result = gateway.send("demo", "stub", request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBizId()).isEqualTo("BIZ-1");
        assertThat(result.getChannel()).isEqualTo("stub");
        assertThat(result.getAppId()).isEqualTo("demo");
        assertThat(result.getRaw()).isEqualTo("stub-ok");
        assertThat(stub.lastRequest).isSameAs(request);
        assertThat(stub.lastConfig.getAppId()).isEqualTo("demo");
        assertThat(gateway.registeredChannels()).containsExactly("stub");
    }

    @Test
    void builderShouldRequireConfigProvider() {
        StubProvider stub = new StubProvider();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                SmsGateway.builder().channel(stub).build())
                .isInstanceOf(io.github.lianghang1.conflux.common.exception.GatewayException.class);
    }
}
