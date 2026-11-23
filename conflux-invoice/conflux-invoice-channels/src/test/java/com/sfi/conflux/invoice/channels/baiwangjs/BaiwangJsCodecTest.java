package com.sfi.conflux.invoice.channels.baiwangjs;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channel.InvoiceContext;
import com.sfi.conflux.invoice.channels.baiwangjs.support.BaiwangJsCodec;
import com.sfi.conflux.invoice.channels.baiwangjs.support.BaiwangJsResponse;
import com.sfi.conflux.invoice.model.Invoice;
import com.sfi.conflux.invoice.model.InvoiceItem;
import com.sfi.conflux.invoice.model.InvoiceState;
import com.sfi.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报文编解码单测：Base64 往返、V1/V2 字段差异、票种映射、同步响应归一。
 *
 * @author lianghang
 */
class BaiwangJsCodecTest {

    @Test
    void encodeRequest_isBase64OfUtf8Json() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("qyId", "Q1");
        body.put("ddlsh", "D1");
        String encoded = BaiwangJsCodec.encodeRequest(body);
        String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonNode root = JsonUtils.readTree(json);
        assertEquals("Q1", root.get("qyId").asText());
        assertEquals("D1", root.get("ddlsh").asText());
    }

    @Test
    void decodeBody_andDecodeOrPlain() {
        String json = "{\"ywlxdm\":\"fpkj\",\"status\":\"1\"}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(json, BaiwangJsCodec.decodeBody(encoded));
        assertEquals(json, BaiwangJsCodec.decodeOrPlain(encoded));
        // 兼容直接推送明文 JSON 的部署
        assertEquals(json, BaiwangJsCodec.decodeOrPlain(json));
        assertThrows(com.sfi.conflux.common.exception.GatewayException.class,
                () -> BaiwangJsCodec.decodeBody("%%%not-base64%%%"));
    }

    @Test
    void issueBody_v2_usesSplitPartyFieldsAndNoXh() {
        Map<String, Object> body = BaiwangJsCodec.issueBody(
                context(false), params(false), "ORDER-1");
        assertEquals("Q1", body.get("qyId"));
        assertEquals("ORDER-1", body.get("ddlsh"));
        assertEquals("91310000TEST000001", body.get("sksbh"));
        assertEquals("01", body.get("fplxdm"));
        assertEquals("0", body.get("kplx"));
        assertEquals("00", body.get("tspz"));
        assertEquals("0", body.get("zsfs"));
        assertEquals("0", body.get("qdbz"));
        assertEquals("133.00", body.get("jshj"));
        assertEquals("13.00", body.get("hjse"));
        assertEquals("120.00", body.get("hjje"));
        assertEquals("SplitAddr", body.get("xsfDz"));
        assertEquals("021-8888", body.get("xsfDh"));
        assertEquals("BuyerAddr", body.get("gmfDz"));
        assertFalse(body.containsKey("xsfDzdh"), "V2 不应使用 V1 合并字段");
        assertFalse(body.containsKey("xh"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertFalse(line.containsKey("xh"), "V2 明细不带 xh");
        assertEquals("0", line.get("fphxz"));
        assertEquals("1", line.get("hsbz"));
        assertEquals("技术服务费", line.get("spmc"));
        assertEquals("3040407990000000000", line.get("spbm"));
        assertEquals("113.00", line.get("je"));
    }

    @Test
    void issueBody_v1_usesTildeJoinedPartyFieldsAndXh() {
        Map<String, Object> body = BaiwangJsCodec.issueBody(
                context(true), params(true), "ORDER-1");
        assertEquals("SplitAddr~~021-8888", body.get("xsfDzdh"));
        assertEquals("Bank~~6222", body.get("xsfYhzh"));
        assertEquals("BuyerAddr~~13800000000", body.get("gmfDzdh"));
        assertFalse(body.containsKey("xsfDz"), "V1 不应使用 V2 拆分字段");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals(1, lines.get(0).get("xh"));
    }

    @Test
    void invoiceTypeCode_defaultsMatchDocumentDictionary() {
        BaiwangJsParams params = params(false);
        assertEquals("01", params.invoiceTypeCode(InvoiceType.DIGITAL_VAT_SPECIAL));
        assertEquals("02", params.invoiceTypeCode(InvoiceType.DIGITAL_VAT_GENERAL));
        assertEquals("03", params.invoiceTypeCode(InvoiceType.VEHICLE_SALES));
        assertEquals("004", params.invoiceTypeCode(InvoiceType.VAT_SPECIAL));
        assertEquals("007", params.invoiceTypeCode(InvoiceType.VAT_GENERAL));
    }

    @Test
    void invoiceTypeCode_configOverrideWins() {
        BaiwangJsParams params = params(false);
        Map<String, String> codes = new LinkedHashMap<String, String>();
        codes.put("DIGITAL_VAT_SPECIAL", "81");
        params.setInvoiceTypeCodes(codes);
        assertEquals("81", params.invoiceTypeCode(InvoiceType.DIGITAL_VAT_SPECIAL));
    }

    @Test
    void pathAndUrl_v2DefaultAndV1Switch() {
        BaiwangJsParams v2 = params(false);
        assertEquals(BaiwangJsSpec.ISSUE_V2,
                v2.path("issuePath", BaiwangJsSpec.ISSUE_V2, BaiwangJsSpec.ISSUE_V1));
        BaiwangJsParams v1 = params(true);
        assertEquals(BaiwangJsSpec.ISSUE_V1,
                v1.path("issuePath", BaiwangJsSpec.ISSUE_V2, BaiwangJsSpec.ISSUE_V1));
        assertEquals(BaiwangJsSpec.QUERY_V1,
                v1.path("queryPath", BaiwangJsSpec.QUERY_V2, BaiwangJsSpec.QUERY_V1));
        assertEquals("https://api.baiwangjs.com" + BaiwangJsSpec.GATEWAY_PATH_PREFIX
                        + "/cloud/v2/applyInvoice",
                v2.url(BaiwangJsSpec.ISSUE_V2));
    }

    @Test
    void path_attrOverrideWins() {
        BaiwangJsParams params = params(false);
        Map<String, String> attr = new LinkedHashMap<String, String>();
        attr.put("issuePath", "/custom/apply");
        params.setAttr(attr);
        assertEquals("/custom/apply",
                params.path("issuePath", BaiwangJsSpec.ISSUE_V2, BaiwangJsSpec.ISSUE_V1));
    }

    @Test
    void parseResponse_andAcceptedOrFailed() {
        BaiwangJsResponse ok = BaiwangJsCodec.parseResponse(
                "{\"code\":\"0\",\"message\":\"成功\",\"renson\":\"\",\"jfmUrl\":\"u1\"}");
        assertTrue(ok.isAccepted());
        assertEquals("0", ok.getCode());
        assertEquals("成功", ok.getMessage());
        assertEquals("u1", ok.dataText("jfmUrl"));

        InvoiceResult accepted = BaiwangJsCodec.acceptedOrFailed(ok, "ORDER-1");
        assertEquals(InvoiceState.PROCESSING, accepted.getState());
        assertEquals("ORDER-1", accepted.getChannelOrderNo());
        assertTrue(accepted.isAsyncAccepted());

        BaiwangJsResponse bad = BaiwangJsCodec.parseResponse(
                "{\"code\":\"E001\",\"message\":\"失败\",\"renson\":\"税号未备案\"}");
        assertFalse(bad.isAccepted());
        InvoiceResult failed = BaiwangJsCodec.acceptedOrFailed(bad, "ORDER-1");
        assertEquals(InvoiceState.FAILED, failed.getState());
        assertEquals("E001", failed.getErrorCode());
        assertEquals("税号未备案", failed.getErrorMsg());
    }

    @Test
    void redApplyBody_usesNegativeAmountsAndCarriesOriginal() {
        Map<String, Object> body = BaiwangJsCodec.redApplyBody(
                context(false), params(false), "RED-1");
        assertEquals("1", body.get("kplx"));
        assertEquals("-133.00", body.get("jshj"));
        assertEquals("-120.00", body.get("hjje"));
        assertEquals("24312000000000000001", body.get("yfpHm"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals("-113.00", lines.get(0).get("je"));
    }

    @Test
    void loginBody_smsOnlyOnSecondStep() {
        BaiwangJsParams params = params(false);
        Map<String, Object> first = BaiwangJsCodec.loginBody(params, null);
        assertFalse(first.containsKey("sms"));
        assertEquals("Q1", first.get("qyId"));
        Map<String, Object> second = BaiwangJsCodec.loginBody(params, "123456");
        assertEquals("123456", second.get("sms"));
    }

    @Test
    void queryBody_onlyRequiresQyIdAndDdlsh() {
        Map<String, Object> body = BaiwangJsCodec.queryBody(params(false), "ORDER-1");
        assertEquals(2, body.size());
        assertEquals("Q1", body.get("qyId"));
        assertEquals("ORDER-1", body.get("ddlsh"));
    }

    @Test
    void nonJsonResponse_failsClearly() {
        assertThrows(com.sfi.conflux.common.exception.GatewayException.class,
                () -> BaiwangJsCodec.parseResponse("<html>error</html>"));
    }

    // ---- 夹具 ----

    private BaiwangJsParams params(boolean v1) {
        BaiwangJsParams p = new BaiwangJsParams()
                .setGatewayKey("my-key").setGatewaySecret("my-secret")
                .setQyId("Q1")
                .setDlzh("13800000000").setKpr("开票员").setSqr("申请人")
                .setSellerName("测试销方").setSellerAddress("SplitAddr")
                .setSellerPhone("021-8888").setSellerBank("Bank")
                .setSellerBankAccount("6222")
                .setApiVersion(v1 ? BaiwangJsApiVersion.V1 : BaiwangJsApiVersion.V2);
        p.setSellerTaxNo("91310000TEST000001");
        return p;
    }

    private InvoiceContext context(boolean v1) {
        InvoiceItem item = new InvoiceItem()
                .setLineNo(1).setItemName("技术服务费")
                .setTaxClassificationCode("3040407990000000000")
                .setAmount(new BigDecimal("113.00"))
                .setTaxAmount(new BigDecimal("13.00"))
                .setTaxRate(new BigDecimal("0.13"))
                .setQuantity(BigDecimal.ONE)
                .setUnitPrice(new BigDecimal("113.00"));
        Invoice invoice = new Invoice()
                .setInvoiceId("INV-1").setConfigId("demo")
                .setBizOrderNo("ORDER-1")
                .setSellerTaxNo("91310000TEST000001").setSellerName("测试销方")
                .setBuyerTitle("测试购方").setBuyerTaxNo("91310000BUYER00001")
                .setBuyerAddress("BuyerAddr").setBuyerPhone("13800000000")
                .setBuyerBank("BuyerBank")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_SPECIAL)
                .setItems(Arrays.asList(item))
                .setAmount(new BigDecimal("133.00"))
                .setTaxAmount(new BigDecimal("13.00"))
                .setInvoiceNo("24312000000000000001")
                .setInvoiceCode("144031609110")
                .setState(InvoiceState.PROCESSING);
        BaiwangJsParams config = params(v1);
        config.setConfigId("demo").setChannelCode(BaiwangJsSpec.CODE)
                .setSellerTaxNo(invoice.getSellerTaxNo())
                .setAttr(Collections.<String, String>emptyMap());
        return InvoiceContext.of(config, null, invoice);
    }
}
