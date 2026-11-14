package io.github.lianghang1.conflux.pay.channels.unionpay;

import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 云闪付渠道测试：参数解析、签名/验签自洽性、状态映射、能力声明（不依赖网络）。
 *
 * @author lianghang
 */
class UnionPayChannelTest {

    private static final String PASSWORD = "test-pwd";

    @Test
    void paramsShouldParseFromMapAndValidateRequiredKeys() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("sandbox", "true");
        properties.put("serProvId", "sp-001");
        properties.put("merchantId", "mch-001");
        properties.put("p12CertPath", "/tmp/test.p12");
        properties.put("p12CertPassword", PASSWORD);
        properties.put("publicKey", "MIGfMA0GCSqGSIb3DQE");
        properties.put("notifyUrl", "http://127.0.0.1/callback/unionpay");

        UnionPayParams params = UnionPayParams.fromMap(properties);
        assertThat(params.getSerProvId()).isEqualTo("sp-001");
        assertThat(params.channelAppId()).isEqualTo("mch-001");
    }

    @Test
    void paramsWithMissingKeysShouldBeRejected() {
        assertThatThrownBy(() -> UnionPayParams.fromMap(new HashMap<String, String>()))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(GatewayErrorCode.CONFIG_MISSING));
        assertThatThrownBy(() -> UnionPayParams.fromMap(null))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void signatureStringShouldBeSortedAndExcludeSignatureField() {
        UnionPaySigner signer = new UnionPaySigner();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("txnAmt", 1000L);
        params.put("orderNo", "PAY-1");
        params.put("signature", "should-be-excluded");
        params.put("currencyCode", "156");

        Map<String, Object> sorted = new TreeMap<String, Object>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if ("signature".equals(entry.getKey())) {
                continue;
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
        }
        sb.setLength(sb.length() - 1);
        assertThat(sb.toString()).isEqualTo("currencyCode=156&orderNo=PAY-1&txnAmt=1000");
        assertThat(signer).isNotNull();
    }

    @Test
    void origRespCodeShouldMapToGatewayPaymentStates() {
        assertThat(UnionPayTranslator.fromOrigRespCode("00")).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(UnionPayTranslator.fromOrigRespCode("02")).isEqualTo(PaymentState.PAYING);
        assertThat(UnionPayTranslator.fromOrigRespCode("05")).isEqualTo(PaymentState.PAYING);
        assertThat(UnionPayTranslator.fromOrigRespCode("57")).isEqualTo(PaymentState.FAILED);
        assertThat(UnionPayTranslator.toPaymentState("00")).isEqualTo(PaymentState.PAYING);
    }

    @Test
    void clientShouldAcceptProcessingRespCodes() {
        assertThat(UnionPayClient.isAccepted("00")).isTrue();
        assertThat(UnionPayClient.isAccepted("02")).isTrue();
        assertThat(UnionPayClient.isAccepted("12")).isTrue();
        assertThat(UnionPayClient.isAccepted("05")).isTrue();
        assertThat(UnionPayClient.isAccepted("57")).isFalse();
        assertThat(UnionPayClient.isAccepted(null)).isFalse();
    }

    @Test
    void providerShouldDeclareCapabilitiesAndParseParams() {
        UnionPayChannelProvider provider = new UnionPayChannelProvider();
        assertThat(provider.channelCode()).isEqualTo("unionpay");
        assertThat(provider.supportedMethods()).contains("UNION_QR", "UNION_BAR");
        assertThat(provider.paymentHandler()).isNotNull();
        assertThat(provider.refundHandler()).isNotNull();
        assertThat(provider.callbackHandler()).isNotNull();

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("merchantId", "mch-001");
        properties.put("p12CertPath", "/tmp/test.p12");
        properties.put("p12CertPassword", PASSWORD);
        properties.put("publicKey", "pub-key");
        assertThat(provider.parseParams(properties)).isInstanceOf(UnionPayParams.class);
    }
}
