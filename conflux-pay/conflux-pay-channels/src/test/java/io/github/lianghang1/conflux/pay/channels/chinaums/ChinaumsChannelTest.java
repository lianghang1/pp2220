package io.github.lianghang1.conflux.pay.channels.chinaums;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.RefundState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 天满渠道测试：参数解析、签名对向量（官方文档）、状态翻译、能力声明、GET 链接构造、回调应答。
 * 不依赖网络与真实凭据。
 *
 * @author lianghang
 */
class ChinaumsChannelTest {

    private static ChinaumsParams params() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("appId", "10037e6f5b8e78f5015b9b0e39690017");
        p.put("appKey", "67890123456789012345678901234567");
        p.put("mid", "898340149000005");
        p.put("tid", "88880001");
        p.put("subAppId", "wx8888888888888888");
        return ChinaumsParams.fromMap(p);
    }

    @Test
    void paramsShouldParseFromMapAndValidateRequiredKeys() {
        ChinaumsParams params = params();
        assertThat(params.getAppId()).isEqualTo("10037e6f5b8e78f5015b9b0e39690017");
        assertThat(params.getMid()).isEqualTo("898340149000005");
        assertThat(params.channelAppId()).isEqualTo("898340149000005");
        assertThat(params.effectiveGateway(false)).isEqualTo(ChinaumsParams.DEFAULT_GATEWAY);
        assertThat(params.effectiveGateway(true)).isEqualTo(ChinaumsParams.SANDBOX_GATEWAY);
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> ChinaumsParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> ChinaumsParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void customGatewayShouldOverrideSandbox() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("appId", "a");
        p.put("appKey", "k");
        p.put("mid", "m");
        p.put("tid", "t");
        p.put("gateway", "https://custom.example.com/");
        ChinaumsParams params = ChinaumsParams.fromMap(p);
        assertThat(params.effectiveGateway(false)).isEqualTo("https://custom.example.com");
    }

    @Test
    void sha256VectorFromDocumentShouldMatch() {
        // 文档：SHA256_WITH_LOWER_HEXSTR([byte 65]) = 559aead0...fdffd
        String b = SignUtil.sha256Hex(new byte[]{65});
        assertThat(b).isEqualTo("559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd");
    }

    @Test
    void hmacSignatureVectorFromDocumentShouldMatch() {
        // 文档对向量：C = appId+timestamp+nonce+B；D = appKey；F = Base64(HmacSHA256(C,D))
        String c = "12345678901234567890123456789012"
                + "20170101120000"
                + "09876543210987654321098765432109"
                + "559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd";
        String d = "67890123456789012345678901234567";
        String f = ChinaumsSigner.base64HmacSha256(c, d);
        assertThat(f).isEqualTo("GINsCTyNKTpEI9KXO16KqZJ64fOyAytEKl8aaR/Dy08=");
    }

    @Test
    void signAuthorizationShouldCarryAllFields() {
        ChinaumsSigner.Signed signed = ChinaumsSigner.sign("{\"a\":1}", params());
        assertThat(signed.authorization).startsWith("OPEN-BODY-SIG AppId=\"10037e6f5b8e78f5015b9b0e39690017\"");
        assertThat(signed.authorization).contains("Timestamp=\"" + signed.timestamp + "\"");
        assertThat(signed.authorization).contains("Nonce=\"" + signed.nonce + "\"");
        assertThat(signed.authorization).contains("Signature=\"");
        assertThat(signed.timestamp).matches("\\d{14}");
        assertThat(signed.nonce).hasSize(32);
    }

    @Test
    void paymentStatesShouldMap() {
        assertThat(ChinaumsStateTranslator.toPaymentState("TRADE_SUCCESS")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(ChinaumsStateTranslator.toPaymentState("TRADE_CLOSED")).isEqualTo(PaymentState.CLOSED);
        assertThat(ChinaumsStateTranslator.toPaymentState("WAIT_BUYER_PAY")).isEqualTo(PaymentState.PAYING);
        assertThat(ChinaumsStateTranslator.toPaymentState("NEW_ORDER")).isEqualTo(PaymentState.PAYING);
        assertThat(ChinaumsStateTranslator.toPaymentState("UNKNOWN")).isEqualTo(PaymentState.FAILED);
        assertThat(ChinaumsStateTranslator.toPaymentState(null)).isEqualTo(PaymentState.PAYING);
    }

    @Test
    void refundStatesShouldMap() {
        assertThat(ChinaumsStateTranslator.toRefundState("SUCCESS", null)).isEqualTo(RefundState.SUCCEEDED);
        assertThat(ChinaumsStateTranslator.toRefundState("FAILED", null)).isEqualTo(RefundState.FAILED);
        assertThat(ChinaumsStateTranslator.toRefundState(null, null)).isEqualTo(RefundState.PROCESSING);
    }

    @Test
    void errCodeSuccessShouldBeRecognized() {
        assertThat(ChinaumsStateTranslator.isSuccess("SUCCESS")).isTrue();
        assertThat(ChinaumsStateTranslator.isSuccess("FAIL")).isFalse();
        assertThat(ChinaumsStateTranslator.isSuccess(null)).isFalse();
    }

    @Test
    void timeShouldParse() {
        assertThat(ChinaumsStateTranslator.toLocalDateTime("2022-04-14 17:29:10")).isNotNull();
        assertThat(ChinaumsStateTranslator.toLocalDateTime("bad")).isNull();
        assertThat(ChinaumsStateTranslator.toLocalDateTime(null)).isNull();
    }

    @Test
    void providerShouldDeclareAllMethodsAndCapabilities() {
        ChinaumsChannelProvider provider = new ChinaumsChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("chinaums");
        assertThat(provider.supportedMethods()).contains(
                "CHINAUMS_WX_APP", "CHINAUMS_WX_H5", "CHINAUMS_WX_MINI", "CHINAUMS_WX_JSAPI",
                "CHINAUMS_ALI_APP", "CHINAUMS_ALI_H5", "CHINAUMS_ALI_MINI", "CHINAUMS_ALI_JSAPI",
                "CHINAUMS_UAC_APP", "CHINAUMS_UAC_H5", "CHINAUMS_UAC_MINI", "CHINAUMS_UAC_JSAPI",
                "CHINAUMS_QR");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();
        assertThat(provider.transferHandler()).isNull();
        assertThat(provider.profitShareHandler()).isNull();
        assertThat(provider.billHandler()).isNull();
    }

    @Test
    void paymentHandlerShouldRegisterAllPayMethods() {
        ChinaumsPaymentHandler handler = new ChinaumsPaymentHandler();
        assertThat(handler.supportedMethods()).hasSize(13);
    }

    @Test
    void buildGetUrlShouldContainSignedQuery() {
        ChannelConfig config = new ChannelConfig().setParams(params());
        Map<String, Object> business = new HashMap<String, Object>();
        business.put("merOrderId", "327M20220419111222");
        business.put("totalAmount", 1L);
        String url = new ChinaumsClient().buildGetUrl("/v1/netpay/wxpay/h5-pay", config, business);
        assertThat(url).startsWith(ChinaumsParams.DEFAULT_GATEWAY + "/v1/netpay/wxpay/h5-pay?");
        assertThat(url).contains("authorization=OPEN-FORM-PARAM");
        assertThat(url).contains("appId=10037e6f5b8e78f5015b9b0e39690017");
        assertThat(url).contains("timestamp=");
        assertThat(url).contains("nonce=");
        assertThat(url).contains("content=");
        assertThat(url).contains("signature=");
        assertThat(url).contains("merOrderId");
    }

    @Test
    void callbackRepliesShouldFollowDocument() {
        ChinaumsCallbackHandler handler = new ChinaumsCallbackHandler(new ChinaumsClient());
        assertThat(handler.successReply()).isEqualTo("SUCCESS");
        assertThat(handler.failureReply()).isEqualTo("FAIL");
    }
}
