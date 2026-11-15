package io.github.lianghang1.conflux.invoice.channels.piaotong;

import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongCallbackParser;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongEnvelope;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongResponse;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票通回调推送解析测试（2.13/2.14 加密信封推送 → 框架语义事件）。
 */
class PiaoTongCallbackParserTest {

    private static PiaoTongParams params;
    private static ChannelConfig config;

    @BeforeAll
    static void init() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        params = new PiaoTongParams()
                .setPlatformCode("PT000001")
                .setAliasName("ABCD")
                .setPassword("0123456789ABCDEF01234567")
                .setPrivateKey(Base64.getEncoder().encodeToString(
                        pair.getPrivate().getEncoded()))
                .setPublicKey(Base64.getEncoder().encodeToString(
                        pair.getPublic().getEncoded()))
                .setSellerTaxNo("911100000000000000");
        config = new ChannelConfig().setParams(params);
    }

    private static InvoiceCallbackRequest push(String businessJson) {
        Map<String, Object> envelope = PiaoTongEnvelope.build(params, businessJson);
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest();
        raw.setChannelCode(PiaoTongSpec.CODE);
        raw.setBody(JsonUtils.toJson(envelope));
        return raw;
    }

    private static String business(String code, String invoiceType) {
        return "{\"taxpayerNum\":\"911100000000000000\","
                + "\"invoiceReqSerialNo\":\"ABCD20240108000000+10\","
                + "\"invoiceType\":\"" + invoiceType + "\","
                + "\"invoiceKind\":\"82\",\"code\":\"" + code + "\","
                + "\"msg\":\"状态描述\",\"invoiceCode\":\"123456789012\","
                + "\"invoiceNo\":\"12345678\",\"invoiceDate\":\"2024-01-08 10:00:00\","
                + "\"amountWithTax\":\"106.00\",\"taxAmount\":\"6.00\"}";
    }

    @Test
    void parse_success_blueInvoice() {
        InvoiceCallbackEvent event = PiaoTongCallbackParser.parse(
                push(business("0000", "1")), config);
        assertEquals(InvoiceState.SUCCEEDED, event.getState());
        assertEquals("ABCD20240108000000+10", event.getChannelOrderNo());
        assertEquals("12345678", event.getInvoiceNo());
        assertEquals("123456789012", event.getInvoiceCode());
        assertTrue(event.getAmount().compareTo(new java.math.BigDecimal("106.00")) == 0);
        assertEquals("0000", event.getAttrs().get("channelResult"));
    }

    @Test
    void parse_failed() {
        InvoiceCallbackEvent event = PiaoTongCallbackParser.parse(
                push(business("9999", "1")), config);
        assertEquals(InvoiceState.FAILED, event.getState());
        assertEquals("9999", event.getErrorCode());
        assertEquals("状态描述", event.getErrorMsg());
    }

    @Test
    void parse_pendingStates() {
        assertEquals(InvoiceState.PROCESSING, PiaoTongCallbackParser.parse(
                push(business("7777", "1")), config).getState());
        assertEquals(InvoiceState.PROCESSING, PiaoTongCallbackParser.parse(
                push(business("5999", "1")), config).getState());
        assertEquals(InvoiceState.PROCESSING, PiaoTongCallbackParser.parse(
                push(business("4999", "2")), config).getState());
        // 红票推送登记 redInvoice 标记
        assertEquals("true", PiaoTongCallbackParser.parse(
                push(business("0000", "2")), config).getAttrs().get("redInvoice"));
    }

    @Test
    void parse_needAuth() {
        String business = "{\"taxpayerNum\":\"911100000000000000\","
                + "\"invoiceReqSerialNo\":\"ABCD20240108000000+10\","
                + "\"code\":\"3999\",\"msg\":\"需要认证\","
                + "\"authenticationQrcode\":\"QRCODE\","
                + "\"authId\":\"AUTH123\"}";
        InvoiceCallbackEvent event = PiaoTongCallbackParser.parse(push(business), config);
        assertEquals(InvoiceState.WAITING_AUTH, event.getState());
        assertEquals("AUTH123", event.getAttrs().get("authId"));
        assertEquals("QRCODE", event.getAttrs().get("authenticationQrcode"));
    }

    @Test
    void parse_tamperedSign_throws() {
        Map<String, Object> envelope = PiaoTongEnvelope.build(params,
                business("0000", "1"));
        envelope.put("sign", "TAMPERED");
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest();
        raw.setChannelCode(PiaoTongSpec.CODE);
        raw.setBody(JsonUtils.toJson(envelope));
        GatewayException e = assertThrows(GatewayException.class,
                () -> PiaoTongCallbackParser.parse(raw, config));
        assertEquals(InvoiceErrorCode.SIGN_INVALID, e.getErrorCode());
    }

    @Test
    void parse_missingBusiness_throws() {
        Map<String, Object> envelope = new HashMap<String, Object>();
        envelope.put("code", "0000");
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest();
        raw.setChannelCode(PiaoTongSpec.CODE);
        raw.setBody(JsonUtils.toJson(envelope));
        assertThrows(GatewayException.class,
                () -> PiaoTongCallbackParser.parse(raw, config));
    }

    @Test
    void reply_withThreadLocalConfig_isEncryptedEnvelope() {
        // 先 parse 暂存配置（同线程），reply 应返回加密信封
        PiaoTongCallbackParser.parse(push(business("0000", "1")), config);
        InvoiceCallbackReply reply = PiaoTongCallbackParser.reply(true, null);
        assertTrue(reply.isSuccess());
        // 应答应为信封：可被本方 parse 回来并解出 code=0000
        PiaoTongResponse response = PiaoTongEnvelope.parse(reply.getBody(), params, true);
        assertEquals("0000", response.bizText("code"));
    }

    @Test
    void reply_withoutConfig_plainBusinessJson() {
        PiaoTongCallbackParser.reply(true, null); // 清理可能残留的配置
        InvoiceCallbackReply reply = PiaoTongCallbackParser.reply(false, "处理失败");
        Map<String, Object> body = JsonUtils.toMap(reply.getBody());
        assertEquals("9999", body.get("code"));
        assertEquals("处理失败", body.get("msg"));
    }

    @Test
    void reply_successPlain_whenNoParse() {
        PiaoTongCallbackParser.reply(true, null); // 清理可能残留的配置
        InvoiceCallbackReply reply = PiaoTongCallbackParser.reply(true, null);
        assertEquals("0000", JsonUtils.toMap(reply.getBody()).get("code"));
    }

    @Test
    void accountKey_fromQueryParams() {
        InvoiceCallbackRequest raw = push(business("0000", "1"));
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("accountKey", "PT000001|911100000000000000");
        raw.setQueryParams(queryParams);
        InvoiceCallbackEvent event = PiaoTongCallbackParser.parse(raw, config);
        assertEquals("PT000001|911100000000000000", event.getAccountKey());
        // 清理 parse 暂存的 ThreadLocal 配置，避免影响后续 reply 用例
        PiaoTongCallbackParser.reply(true, null);
    }

    @Test
    void provider_wiring() {
        PiaoTongChannelProvider provider = new PiaoTongChannelProvider();
        assertEquals(PiaoTongSpec.CODE, provider.channelCode());
        assertTrue(provider.supportedTypes().contains(InvoiceType.DIGITAL_VAT_SPECIAL));
        assertTrue(provider.supportedTypes().contains(InvoiceType.DIGITAL_VAT_GENERAL));
        assertTrue(provider.issuer() == provider);
        assertTrue(provider.querier() == provider);
        assertTrue(provider.fileFetcher() == provider);
        assertTrue(provider.redHandler() == provider);
        assertTrue(provider.authHandler() == provider);
        assertTrue(provider.callbackHandler() == provider);
        assertTrue(provider.deliverer() == provider);

        Map<String, String> props = new HashMap<String, String>();
        props.put("platformCode", "PT000001");
        props.put("aliasName", "ABCD");
        props.put("password", "0123456789ABCDEF01234567");
        props.put("privateKey", "PRI");
        props.put("publicKey", "PUB");
        props.put("sellerTaxNo", "911100000000000000");
        PiaoTongParams parsed = (PiaoTongParams) provider.parseParams(props);
        assertEquals("PT000001", parsed.getPlatformCode());
        assertEquals("ABCD", parsed.getAliasName());
        // 账户键 = channelAppId（platformCode）+ sellerTaxNo 复合定位
        assertEquals("PT000001", parsed.channelAppId());
        assertEquals("911100000000000000", parsed.sellerTaxNo());
        assertTrue(parsed.hasCredentials());
    }
}
