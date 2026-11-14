package io.github.lianghang1.paygateway.channels.alipay;

import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.TransferState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付宝渠道测试：参数解析、状态与金额映射、能力声明与回调应答（不依赖网络与真实凭据）。
 *
 * @author lianghang
 */
class AlipayChannelTest {

    @Test
    void paramsShouldParseFromMapAndValidateRequiredKeys() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("appId", "2021000000000001");
        properties.put("privateKey", "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----");
        properties.put("alipayPublicKey", "MIIBIjANBgkq");

        AlipayParams params = AlipayParams.fromMap(properties);
        assertThat(params.getAppId()).isEqualTo("2021000000000001");
        assertThat(params.getSignType()).isEqualTo("RSA2");
        assertThat(params.getGateway()).isEqualTo(AlipayParams.DEFAULT_GATEWAY);
        assertThat(params.channelAppId()).isEqualTo("2021000000000001");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> AlipayParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> AlipayParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void tradeStatusShouldMapToGatewayPaymentStates() {
        assertThat(AlipayStateTranslator.toPaymentState("TRADE_SUCCESS")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(AlipayStateTranslator.toPaymentState("TRADE_FINISHED")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(AlipayStateTranslator.toPaymentState("TRADE_CLOSED")).isEqualTo(PaymentState.CLOSED);
        assertThat(AlipayStateTranslator.toPaymentState("WAIT_BUYER_PAY")).isEqualTo(PaymentState.PAYING);
        assertThat(AlipayStateTranslator.toPaymentState(null)).isEqualTo(PaymentState.PAYING);
    }

    @Test
    void amountConversionShouldRoundTripViaFen() {
        assertThat(AlipayStateTranslator.toFen("12.34")).isEqualTo(1234L);
        assertThat(AlipayStateTranslator.toYuan(1234L)).isEqualTo("12.34");
        assertThat(AlipayStateTranslator.toFen(null)).isNull();
        assertThat(AlipayStateTranslator.toYuan(null)).isNull();
    }

    @Test
    void transferStatesShouldMap() {
        assertThat(AlipayStateTranslator.toTransferState("SUCCESS")).isEqualTo(TransferState.SUCCEEDED);
        assertThat(AlipayStateTranslator.toTransferState("FAIL")).isEqualTo(TransferState.FAILED);
        assertThat(AlipayStateTranslator.toTransferState("DEALING")).isEqualTo(TransferState.PROCESSING);
        assertThat(AlipayStateTranslator.toTransferState(null)).isEqualTo(TransferState.PROCESSING);
    }

    @Test
    void providerShouldDeclareCapabilitiesAndParseParams() {
        AlipayChannelProvider provider = new AlipayChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("alipay");
        assertThat(provider.supportedMethods()).contains("ALI_QR", "ALI_WAP", "ALI_PAGE", "ALI_APP", "ALI_JSAPI", "ALI_BAR", "ALI_TRANSFER");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.transferHandler()).isNotNull();
        assertThat(provider.profitShareHandler()).isNotNull();
        assertThat(provider.billHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("appId", "2021000000000001");
        properties.put("privateKey", "key");
        properties.put("alipayPublicKey", "pub");
        assertThat(provider.parseParams(properties)).isInstanceOf(AlipayParams.class);
    }

    @Test
    void callbackRepliesShouldBePlainTextSuccess() {
        AlipayCallbackHandler handler = new AlipayCallbackHandler(new AlipayClientHolder());
        assertThat(handler.successReply()).isEqualTo("success");
        assertThat(handler.failureReply()).isEqualTo("fail");
    }
}
