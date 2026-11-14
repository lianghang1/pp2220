package io.github.lianghang1.paygateway.channels.wechat;

import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import io.github.lianghang1.paygateway.model.RefundState;
import io.github.lianghang1.paygateway.model.TransferState;
import io.github.lianghang1.paygateway.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 微信渠道测试：参数解析、状态映射、回调报文模型与应答结构（不依赖网络与真实凭据）。
 *
 * @author lianghang
 */
class WechatChannelTest {

    @Test
    void paramsShouldParseFromMapAndValidateRequiredKeys() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("merchantId", "mch-001");
        properties.put("merchantSerialNumber", "serial-001");
        properties.put("merchantPrivateKey", "-----BEGIN PRIVATE KEY-----\\nMIIB\\n-----END PRIVATE KEY-----");
        properties.put("apiV3Key", "v3-key-001");
        properties.put("appId", "wx-app-001");
        properties.put("notifyUrl", "http://127.0.0.1/callback/wechat");

        WechatParams params = WechatParams.fromMap(properties);
        assertThat(params.getMerchantId()).isEqualTo("mch-001");
        assertThat(params.getAppId()).isEqualTo("wx-app-001");
        assertThat(params.channelAppId()).isEqualTo("wx-app-001");
        assertThat(params.getNotifyUrl()).isEqualTo("http://127.0.0.1/callback/wechat");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> WechatParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> WechatParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void tradeStatesShouldMapToGatewayPaymentStates() {
        assertThat(WechatStateTranslator.toPaymentState("SUCCESS")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(WechatStateTranslator.toPaymentState("REFUND")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(WechatStateTranslator.toPaymentState("ACCEPT")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(WechatStateTranslator.toPaymentState("CLOSED")).isEqualTo(PaymentState.CLOSED);
        assertThat(WechatStateTranslator.toPaymentState("REVOKED")).isEqualTo(PaymentState.CLOSED);
        assertThat(WechatStateTranslator.toPaymentState("PAYERROR")).isEqualTo(PaymentState.FAILED);
        assertThat(WechatStateTranslator.toPaymentState("NOTPAY")).isEqualTo(PaymentState.PAYING);
        assertThat(WechatStateTranslator.toPaymentState("USERPAYING")).isEqualTo(PaymentState.PAYING);
        assertThat(WechatStateTranslator.toPaymentState(null)).isEqualTo(PaymentState.PAYING);
    }

    @Test
    void refundAndTransferStatesShouldMap() {
        assertThat(WechatStateTranslator.toRefundState("SUCCESS")).isEqualTo(RefundState.SUCCEEDED);
        assertThat(WechatStateTranslator.toRefundState("CLOSED")).isEqualTo(RefundState.CLOSED);
        assertThat(WechatStateTranslator.toRefundState("ABNORMAL")).isEqualTo(RefundState.FAILED);
        assertThat(WechatStateTranslator.toRefundState("PROCESSING")).isEqualTo(RefundState.PROCESSING);
        assertThat(WechatStateTranslator.toRefundState(null)).isEqualTo(RefundState.PROCESSING);

        assertThat(WechatStateTranslator.toTransferState("SUCCESS")).isEqualTo(TransferState.SUCCEEDED);
        assertThat(WechatStateTranslator.toTransferState("FINISH")).isEqualTo(TransferState.SUCCEEDED);
        assertThat(WechatStateTranslator.toTransferState("FAIL")).isEqualTo(TransferState.FAILED);
        assertThat(WechatStateTranslator.toTransferState("PROCESSING")).isEqualTo(TransferState.PROCESSING);

        assertThat(WechatStateTranslator.toProfitShareState("FINISHED")).isEqualTo(ProfitShareState.FINISHED);
        assertThat(WechatStateTranslator.toProfitShareState("CLOSED")).isEqualTo(ProfitShareState.FAILED);
        assertThat(WechatStateTranslator.toProfitShareState("PROCESSING")).isEqualTo(ProfitShareState.PROCESSING);
    }

    @Test
    void isoTimeShouldTranslateToLocalDateTime() {
        LocalDateTime time = WechatStateTranslator.toLocalDateTime("2026-11-14T15:00:00+08:00");
        assertThat(time).isNotNull();
        assertThat(time.getHour()).isEqualTo(15);
        assertThat(WechatStateTranslator.toLocalDateTime("bad")).isNull();
        assertThat(WechatStateTranslator.toWechatTime(LocalDateTime.of(2026, 11, 14, 15, 0)))
                .contains("2026-11-14T15:00:00");
    }

    @Test
    void callbackPayloadShouldDeserializeSnakeCaseJson() {
        String json = "{"
                + "\"mchid\":\"mch-001\","
                + "\"out_trade_no\":\"PAY-1\","
                + "\"transaction_id\":\"WX-TXN-1\","
                + "\"trade_state\":\"SUCCESS\","
                + "\"success_time\":\"2026-11-14T15:00:00+08:00\","
                + "\"amount\":{\"total\":2500,\"payer_total\":2500}"
                + "}";
        WechatCallbackPayload payload = JsonUtils.fromJson(json, WechatCallbackPayload.class);
        assertThat(payload.getOutTradeNo()).isEqualTo("PAY-1");
        assertThat(payload.getTransactionId()).isEqualTo("WX-TXN-1");
        assertThat(payload.getTradeState()).isEqualTo("SUCCESS");
        assertThat(payload.getAmount().getTotal()).isEqualTo(2500L);

        WechatCallbackPayload refundPayload = JsonUtils.fromJson(
                "{\"out_refund_no\":\"RFD-1\",\"refund_id\":\"WX-RFD-1\",\"refund_status\":\"SUCCESS\"}",
                WechatCallbackPayload.class);
        assertThat(refundPayload.getOutRefundNo()).isEqualTo("RFD-1");
        assertThat(refundPayload.getRefundStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void providerShouldDeclareCapabilitiesAndParseParams() {
        WechatChannelProvider provider = new WechatChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("wechat");
        assertThat(provider.supportedMethods()).contains("WECHAT_JSAPI", "WECHAT_NATIVE", "WECHAT_H5", "WECHAT_APP", "WECHAT_TRANS");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.transferHandler()).isNotNull();
        assertThat(provider.profitShareHandler()).isNotNull();
        assertThat(provider.billHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("merchantId", "mch-001");
        properties.put("merchantSerialNumber", "serial-001");
        properties.put("merchantPrivateKey", "key");
        properties.put("apiV3Key", "v3-key");
        properties.put("appId", "wx-app");
        assertThat(provider.parseParams(properties)).isInstanceOf(WechatParams.class);
    }

    @Test
    void callbackRepliesShouldBeJsonStructure() {
        WechatCallbackHandler handler = new WechatCallbackHandler(new WechatClientHolder());
        assertThat(handler.successReply()).contains("\"code\":\"SUCCESS\"");
        assertThat(handler.failureReply()).contains("\"code\":\"FAIL\"");
    }
}
