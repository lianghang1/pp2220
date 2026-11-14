package io.github.lianghang1.paygateway.channels.paypal;

import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.RefundState;
import io.github.lianghang1.paygateway.model.TransferState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PayPal 渠道测试：参数解析、状态与金额映射、链接提取、能力声明（不依赖网络）。
 *
 * @author lianghang
 */
class PayPalChannelTest {

    @Test
    void paramsShouldParseFromMapAndValidateRequiredKeys() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("sandbox", "true");
        properties.put("clientId", "cid-001");
        properties.put("clientSecret", "secret-001");
        properties.put("webhookId", "wh-001");

        PayPalParams params = PayPalParams.fromMap(properties);
        assertThat(params.channelAppId()).isEqualTo("cid-001");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> PayPalParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> PayPalParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void orderStatusShouldMapToGatewayPaymentStates() {
        assertThat(PayPalTranslator.toPaymentState("COMPLETED")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(PayPalTranslator.toPaymentState("VOIDED")).isEqualTo(PaymentState.CLOSED);
        assertThat(PayPalTranslator.toPaymentState("APPROVED")).isEqualTo(PaymentState.PAYING);
        assertThat(PayPalTranslator.toPaymentState("CREATED")).isEqualTo(PaymentState.PAYING);
        assertThat(PayPalTranslator.toPaymentState(null)).isEqualTo(PaymentState.PAYING);
    }

    @Test
    void refundAndTransferStatesShouldMap() {
        assertThat(PayPalTranslator.toRefundState("COMPLETED")).isEqualTo(RefundState.SUCCEEDED);
        assertThat(PayPalTranslator.toRefundState("PENDING")).isEqualTo(RefundState.PROCESSING);
        assertThat(PayPalTranslator.toTransferState("SUCCESS")).isEqualTo(TransferState.SUCCEEDED);
        assertThat(PayPalTranslator.toTransferState("FAILED")).isEqualTo(TransferState.FAILED);
        assertThat(PayPalTranslator.toTransferState("DENIED")).isEqualTo(TransferState.FAILED);
        assertThat(PayPalTranslator.toTransferState("PENDING")).isEqualTo(TransferState.PROCESSING);
        assertThat(PayPalTranslator.toTransferState(null)).isEqualTo(TransferState.PROCESSING);
    }

    @Test
    void amountConversionShouldRoundTripViaFen() {
        assertThat(PayPalTranslator.toYuan(1250L)).isEqualTo("12.50");
        assertThat(PayPalTranslator.toFen("12.50")).isEqualTo(1250L);
        assertThat(PayPalTranslator.toFen(null)).isNull();
    }

    @Test
    void linkExtractorShouldFindRelHref() {
        Map<String, Object> approve = new HashMap<String, Object>();
        approve.put("rel", "approve");
        approve.put("href", "https://www.sandbox.paypal.com/checkoutnow?token=abc");
        Map<String, Object> self = new HashMap<String, Object>();
        self.put("rel", "self");
        self.put("href", "https://api-m.sandbox.paypal.com/v2/checkout/orders/abc");
        Map<String, Object> order = new HashMap<String, Object>();
        order.put("links", java.util.Arrays.asList(approve, self));

        assertThat(PayPalTranslator.link(order, "approve")).contains("checkoutnow");
        assertThat(PayPalTranslator.link(order, "unknown")).isNull();
    }

    @Test
    void providerShouldDeclareCapabilitiesAndParseParams() {
        PayPalChannelProvider provider = new PayPalChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("paypal");
        assertThat(provider.supportedMethods()).contains("PAYPAL_SALE", "PAYPAL_PAYOUT");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.transferHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("clientId", "cid-001");
        properties.put("clientSecret", "secret-001");
        assertThat(provider.parseParams(properties)).isInstanceOf(PayPalParams.class);
    }
}
