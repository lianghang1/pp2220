package io.github.lianghang1.conflux.pay.channels.wechat;

import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 微信渠道测试：V2/V3 参数解析校验、状态映射、paymethod 路由与能力声明
 * （不依赖网络与真实凭据）。
 *
 * @author lianghang
 */
class WechatChannelTest {

    private Map<String, String> v3Properties() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("merchantId", "mch-001");
        properties.put("appId", "wx-app-001");
        properties.put("apiV3Key", "v3-key-001");
        properties.put("merchantSerialNumber", "serial-001");
        properties.put("merchantPrivateKey", "-----BEGIN PRIVATE KEY-----\\nMIIB\\n-----END PRIVATE KEY-----");
        return properties;
    }

    @Test
    void v3ParamsShouldParseAndValidate() {
        WechatParams params = WechatParams.fromMap(v3Properties());
        assertThat(params.getMerchantId()).isEqualTo("mch-001");
        assertThat(params.getAppId()).isEqualTo("wx-app-001");
        assertThat(params.channelAppId()).isEqualTo("wx-app-001");
        assertThat(params.isV2()).isFalse();
    }

    @Test
    void v2ParamsShouldRequireApiV2Key() {
        Map<String, String> properties = v3Properties();
        properties.remove("apiV3Key");
        properties.remove("merchantSerialNumber");
        properties.remove("merchantPrivateKey");
        properties.put("wxApiVersion", "v2");
        properties.put("apiV2Key", "v2-key-001");

        WechatParams params = WechatParams.fromMap(properties);
        assertThat(params.isV2()).isTrue();
        assertThat(params.getApiV2Key()).isEqualTo("v2-key-001");
        // 企业付款密钥缺省回落 V2 支付密钥
        assertThat(params.effectiveEntPayKey()).isEqualTo("v2-key-001");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> WechatParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        // V3 模式缺 V3 凭据
        Map<String, String> partial = new HashMap<String, String>();
        partial.put("merchantId", "mch-001");
        partial.put("appId", "wx-app-001");
        assertThatThrownBy(() -> WechatParams.fromMap(partial))
                .isInstanceOf(GatewayException.class);
        // V2 模式缺 V2 密钥
        Map<String, String> v2WithoutKey = v3Properties();
        v2WithoutKey.put("wxApiVersion", "v2");
        v2WithoutKey.remove("apiV3Key");
        v2WithoutKey.remove("merchantSerialNumber");
        v2WithoutKey.remove("merchantPrivateKey");
        assertThatThrownBy(() -> WechatParams.fromMap(v2WithoutKey))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void tradeStatesShouldMapToGatewayPaymentStates() {
        assertThat(WechatStateTranslator.toPaymentState("SUCCESS")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(WechatStateTranslator.toPaymentState("REFUND")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(WechatStateTranslator.toPaymentState("CLOSED")).isEqualTo(PaymentState.CLOSED);
        assertThat(WechatStateTranslator.toPaymentState("REVOKED")).isEqualTo(PaymentState.CLOSED);
        assertThat(WechatStateTranslator.toPaymentState("PAYERROR")).isEqualTo(PaymentState.FAILED);
        assertThat(WechatStateTranslator.toPaymentState("NOTPAY")).isEqualTo(PaymentState.PAYING);
        // ACCEPT=已接收待扣款，属支付中，不是支付成功
        assertThat(WechatStateTranslator.toPaymentState("ACCEPT")).isEqualTo(PaymentState.PAYING);
        assertThat(WechatStateTranslator.toPaymentState(null)).isEqualTo(PaymentState.PAYING);

        assertThat(WechatStateTranslator.toRefundState("SUCCESS")).isEqualTo(io.github.lianghang1.conflux.pay.model.RefundState.SUCCEEDED);
        assertThat(WechatStateTranslator.toRefundState("ABNORMAL")).isEqualTo(io.github.lianghang1.conflux.pay.model.RefundState.FAILED);
        assertThat(WechatStateTranslator.toRefundState(null)).isEqualTo(io.github.lianghang1.conflux.pay.model.RefundState.PROCESSING);
    }

    @Test
    void providerShouldDeclareCapabilitiesAndParseParams() {
        WechatChannelProvider provider = new WechatChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("wechat");
        assertThat(provider.supportedMethods()).contains(
                "WECHAT_JSAPI", "WECHAT_LITE", "WECHAT_NATIVE", "WECHAT_H5", "WECHAT_APP", "WECHAT_BAR", "WECHAT_TRANS");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.transferHandler()).isNotNull();
        assertThat(provider.profitShareHandler()).isNotNull();
        assertThat(provider.billHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();

        assertThat(provider.parseParams(v3Properties())).isInstanceOf(WechatParams.class);
    }

    @Test
    void paymentHandlerShouldRouteByMethod() {
        WechatPaymentHandler handler = new WechatPaymentHandler();
        assertThat(handler.supportedMethods()).contains(
                "WECHAT_JSAPI", "WECHAT_LITE", "WECHAT_NATIVE", "WECHAT_H5", "WECHAT_APP", "WECHAT_BAR");
        // 未注册的方式按渠道异常拦截（不触发渠道调用）
        assertThatThrownBy(() -> handler.createPayment(
                new io.github.lianghang1.conflux.pay.model.Payment().setMethod("NOT_REGISTERED"), null))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.METHOD_NOT_SUPPORTED));
    }

    @Test
    void callbackRepliesShouldBeJsonStructure() {
        WechatCallbackHandler handler = new WechatCallbackHandler(new WechatClientHolder());
        assertThat(handler.successReply()).contains("\"code\":\"SUCCESS\"");
        assertThat(handler.failureReply()).contains("\"code\":\"FAIL\"");
    }
}
