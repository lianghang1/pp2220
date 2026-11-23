package com.sfi.conflux.invoice.channels.nuonuo;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.invoice.api.InvoiceCallbackEvent;
import com.sfi.conflux.invoice.api.InvoiceCallbackRequest;
import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channel.InvoiceContext;
import com.sfi.conflux.invoice.channels.nuonuo.support.NuonuoCallbackParser;
import com.sfi.conflux.invoice.channels.nuonuo.support.NuonuoCodec;
import com.sfi.conflux.invoice.model.ChannelTier;
import com.sfi.conflux.invoice.model.Invoice;
import com.sfi.conflux.invoice.model.InvoiceItem;
import com.sfi.conflux.invoice.model.InvoiceState;
import com.sfi.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 诺诺渠道单测：配置解析、能力声明、开票/查单报文组装、响应归一（result 三种形态）、
 * 状态映射、回调解析与应答。全部离线，不依赖网络与真实凭据。
 *
 * @author lianghang
 */
class NuonuoChannelTest {

    private final NuonuoChannelProvider provider = new NuonuoChannelProvider();

    // ---------- 配置解析 ----------

    @Test
    void parseParams_readsAliasesAndSandboxGateway() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("appKey", "APPKEY");
        p.put("appSecret", "SECRET");
        p.put("salerTaxNum", "91310000TEST000001");
        p.put("sandbox", "true");
        NuonuoParams params = (NuonuoParams) provider.parseParams(p);
        assertEquals("APPKEY", params.getAppKey());
        assertEquals("SECRET", params.getAppSecret());
        assertEquals("91310000TEST000001", params.getSellerTaxNo());
        assertThat(params.isSandbox()).isTrue();
        assertEquals(NuonuoSpec.SANDBOX_GATEWAY, params.getGateway());
        assertEquals("APPKEY", params.channelAppId());
        assertThat(params.hasCredentials()).isTrue();
        assertThat(params.requiresToken()).isFalse(); // 沙箱不需要 token
    }

    @Test
    void parseParams_productionRequiresTokenAndDefaultGateway() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("appKey", "K");
        p.put("appSecret", "S");
        NuonuoParams params = (NuonuoParams) provider.parseParams(p);
        assertEquals(NuonuoSpec.DEFAULT_GATEWAY, params.getGateway());
        assertThat(params.requiresToken()).isTrue();
    }

    // ---------- 能力声明 ----------

    @Test
    void capabilities_matchDocumentedScope() {
        assertEquals("nuonuo", provider.channelCode());
        assertEquals(ChannelTier.SERVICE_API, provider.channelTier());
        assertThat(provider.issuer()).isSameAs(provider);
        assertThat(provider.querier()).isSameAs(provider);
        assertThat(provider.callbackHandler()).isSameAs(provider);
        // 未核对能力不暴露
        assertNull(provider.redHandler());
        assertNull(provider.deliverer());
        assertNull(provider.authHandler());
        assertNull(provider.repairer());
    }

    // ---------- 开票报文 ----------

    @Test
    void issueContent_wrapsOrderWithDetailAndParties() {
        String json = NuonuoCodec.issueContent(context(), params(), "ORDER-1", "http://cb");
        JsonNode root = JsonUtils.readTree(json);
        JsonNode order = root.get("order");
        assertThat(order).isNotNull();
        assertEquals("ORDER-1", order.get("orderNo").asText());
        assertEquals("http://cb", order.get("callBackUrl").asText());
        assertEquals("1", order.get("invoiceType").asText());
        assertEquals("91310000TEST000001", order.get("salerTaxNum").asText());
        assertEquals("测试购方", order.get("buyerName").asText());
        JsonNode detail = order.get("invoiceDetail").get(0);
        assertEquals("技术服务费", detail.get("goodsName").asText());
        assertEquals("3040407990000000000", detail.get("goodsCode").asText());
        assertEquals(0, new BigDecimal("13.00").compareTo(detail.get("tax").decimalValue()));
        assertEquals("0", detail.get("withTaxFlag").asText());
    }

    @Test
    void queryContent_usesSerialAndOrderArrays() {
        String json = NuonuoCodec.queryContent("SER-1", "ORDER-1");
        JsonNode root = JsonUtils.readTree(json);
        assertEquals("SER-1", root.get("serialNos").get(0).asText());
        assertEquals("ORDER-1", root.get("orderNos").get(0).asText());
        assertEquals("1", root.get("isOfferInvoiceDetail").asText());
    }

    // ---------- 响应归一：result 三种形态 ----------

    @Test
    void issueResult_parsesObjectResult() {
        String resp = "{\"code\":\"E0000\",\"describe\":\"开票提交成功\","
                + "\"result\":{\"invoiceSerialNum\":\"SER-1\"}}";
        InvoiceResult r = NuonuoCodec.issueResult(resp, "ORDER-1");
        assertEquals(InvoiceState.PROCESSING, r.getState());
        assertEquals("SER-1", r.getChannelOrderNo());
        assertEquals("ORDER-1", r.getBizOrderNo());
        assertThat(r.isAsyncAccepted()).isTrue();
    }

    @Test
    void issueResult_parsesStringResult() {
        // 部分环境 result 是 JSON 字符串，需二次解析
        String resp = "{\"code\":\"E0000\",\"result\":\"{\\\"invoiceSerialNum\\\":\\\"SER-2\\\"}\"}";
        InvoiceResult r = NuonuoCodec.issueResult(resp, "ORDER-2");
        assertEquals("SER-2", r.getChannelOrderNo());
    }

    @Test
    void issueResult_failedOnBillingError() {
        String resp = "{\"code\":\"E9106\",\"describe\":\"订单编号重复\"}";
        InvoiceResult r = NuonuoCodec.issueResult(resp, "ORDER-1");
        assertEquals(InvoiceState.FAILED, r.getState());
        assertEquals("E9106", r.getErrorCode());
    }

    @Test
    void queryResult_parsesArrayResult_success() {
        String resp = "{\"code\":\"H200\",\"result\":[{"
                + "\"serialNo\":\"SER-1\",\"orderNo\":\"ORDER-1\",\"status\":\"2\","
                + "\"invoiceNo\":\"24312000000022669098\",\"invoiceCode\":\"144031609110\","
                + "\"invoiceTime\":\"2024-01-22 15:30:00\"}]}";
        InvoiceResult r = NuonuoCodec.queryResult(resp, "SER-1", "ORDER-1");
        assertEquals(InvoiceState.SUCCEEDED, r.getState());
        assertEquals("24312000000022669098", r.getInvoiceNo());
        assertEquals("SER-1", r.getChannelOrderNo());
    }

    @Test
    void queryResult_parsesStringResultArray_andProcessingState() {
        String resp = "{\"code\":\"H200\",\"result\":\"[{\\\"serialNo\\\":\\\"SER-1\\\","
                + "\\\"status\\\":\\\"20\\\",\\\"statusMsg\\\":\\\"开票中\\\"}]\"}";
        InvoiceResult r = NuonuoCodec.queryResult(resp, "SER-1", "ORDER-1");
        assertEquals(InvoiceState.PROCESSING, r.getState());
    }

    @Test
    void queryResult_emptyResultKeepsProcessing() {
        InvoiceResult r = NuonuoCodec.queryResult("{\"code\":\"H200\",\"result\":[]}", "SER-1", "ORDER-1");
        assertEquals(InvoiceState.PROCESSING, r.getState());
        assertEquals("SER-1", r.getChannelOrderNo());
    }

    // ---------- 状态映射 ----------

    @Test
    void stateMappings_areDistinctForQueryAndNotice() {
        // 查询 status：2 成功 / 20 21 处理中 / 22 失败
        assertEquals(InvoiceState.SUCCEEDED, NuonuoCodec.stateOfQuery("2"));
        assertEquals(InvoiceState.PROCESSING, NuonuoCodec.stateOfQuery("20"));
        assertEquals(InvoiceState.PROCESSING, NuonuoCodec.stateOfQuery("21"));
        assertEquals(InvoiceState.FAILED, NuonuoCodec.stateOfQuery("22"));
        assertEquals(InvoiceState.PROCESSING, NuonuoCodec.stateOfQuery("99"));
        // 回调 status（语义不同）：1 成功 / 2 3 失败
        assertEquals(InvoiceState.SUCCEEDED, NuonuoCodec.stateOfNotice("1"));
        assertEquals(InvoiceState.FAILED, NuonuoCodec.stateOfNotice("2"));
        assertEquals(InvoiceState.FAILED, NuonuoCodec.stateOfNotice("3"));
    }

    // ---------- 回调 ----------

    @Test
    void callbackParse_successNotice() {
        String body = "{\"action\":\"nuonuo_billing_new_notice\","
                + "\"sale_tax_num\":\"91310000TEST000001\",\"order_no\":\"ORDER-1\","
                + "\"invoice_no\":\"24312000000022669098\","
                + "\"invoice_down_url\":\"https://inv.jss.com.cn/fp2/x.pdf\","
                + "\"status\":1,\"message\":\"\"}";
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode("nuonuo").setBody(body);
        InvoiceCallbackEvent event = NuonuoCallbackParser.parse(raw, null);
        assertEquals(InvoiceState.SUCCEEDED, event.getState());
        assertEquals("ORDER-1", event.getBizOrderNo());
        assertEquals("24312000000022669098", event.getInvoiceNo());
        assertThat(event.getFiles()).hasSize(1);
        assertEquals("https://inv.jss.com.cn/fp2/x.pdf", event.getFiles().get(0).getUrl());
    }

    @Test
    void callbackParse_failedNoticeCarriesMessage() {
        String body = "{\"action\":\"nuonuo_billing_new_notice\",\"order_no\":\"ORDER-2\","
                + "\"status\":2,\"message\":\"需登录电子发票服务平台\"}";
        InvoiceCallbackEvent event = NuonuoCallbackParser.parse(
                new InvoiceCallbackRequest().setBody(body), null);
        assertEquals(InvoiceState.FAILED, event.getState());
        assertEquals("需登录电子发票服务平台", event.getErrorMsg());
    }

    @Test
    void callbackReply_isSuccessString() {
        assertThat(NuonuoCallbackParser.reply(true, null).getBody())
                .isEqualTo(NuonuoSpec.REPLY_SUCCESS);
        assertThat(NuonuoCallbackParser.reply(false, "x").getBody())
                .isEqualTo(NuonuoSpec.REPLY_SUCCESS);
    }

    // ---------- 辅助 ----------

    private static NuonuoParams params() {
        NuonuoParams p = new NuonuoParams()
                .setAppKey("K").setAppSecret("S")
                .setGateway(NuonuoSpec.SANDBOX_GATEWAY);
        p.setSellerTaxNo("91310000TEST000001");
        p.setSandbox(true);
        return p;
    }

    private static InvoiceContext context() {
        InvoiceItem item = new InvoiceItem()
                .setLineNo(1).setItemName("技术服务费")
                .setTaxClassificationCode("3040407990000000000")
                .setAmount(new BigDecimal("100.00"))
                .setTaxAmount(new BigDecimal("13.00"))
                .setTaxRate(new BigDecimal("0.13"))
                .setQuantity(BigDecimal.ONE)
                .setUnitPrice(new BigDecimal("100.00"));
        Invoice invoice = new Invoice()
                .setInvoiceId("INV-1").setConfigId("demo")
                .setBizOrderNo("ORDER-1")
                .setSellerTaxNo("91310000TEST000001").setSellerName("测试销方")
                .setBuyerTitle("测试购方").setBuyerTaxNo("91310000BUYER00001")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Arrays.asList(item))
                .setAmount(new BigDecimal("113.00"))
                .setTaxAmount(new BigDecimal("13.00"))
                .setState(InvoiceState.PROCESSING);
        ChannelConfig config = params()
                .setConfigId("demo").setChannelCode(NuonuoSpec.CODE)
                .setAttr(new LinkedHashMap<String, String>());
        config.setSellerTaxNo(invoice.getSellerTaxNo());
        return InvoiceContext.of(config, null, invoice);
    }
}
