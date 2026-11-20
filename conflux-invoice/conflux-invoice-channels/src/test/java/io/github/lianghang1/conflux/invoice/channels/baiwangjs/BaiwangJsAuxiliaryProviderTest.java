package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsCallbackParser;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 百旺金穗云 A+B 档 Provider / 回调单测：只覆盖不发网络请求的分支
 * （能力暴露、配置解析、参数校验、凭据 fail-fast、辅助查询降级、hzcx 回调分流）。
 *
 * <p>真实 HTTP 调用属于渠道联调范畴，按项目口径不在无网络单测内模拟。</p>
 *
 * @author lianghang
 */
class BaiwangJsAuxiliaryProviderTest {

    private static final String TAX = "91310000TEST000001";

    @Test
    void exposesNewOptionalCapabilities() {
        BaiwangJsChannelProvider p = new BaiwangJsChannelProvider();
        assertSame(p, p.deliverer());
        assertSame(p, p.repairer());
        assertSame(p, p.issuer());
        assertSame(p, p.redHandler());
        assertEquals(BaiwangJsSpec.CODE, p.channelCode());
    }

    @Test
    void parseParams_readsCredentialsVersionAndTypeCodes() {
        Map<String, String> props = new LinkedHashMap<String, String>();
        props.put("gatewayKey", "k");
        props.put("gatewaySecret", "s");
        props.put("qyId", "Q1");
        props.put("apiVersion", "V1");
        props.put("sellerTaxNo", TAX);
        props.put("fplxdm.DIGITAL_VAT_SPECIAL", "81");
        BaiwangJsParams params = (BaiwangJsParams) new BaiwangJsChannelProvider()
                .parseParams(props);
        assertEquals("k", params.getGatewayKey());
        assertEquals("s", params.getGatewaySecret());
        assertEquals("Q1", params.getQyId());
        assertTrue(params.isV1());
        assertEquals("k", params.channelAppId());
        assertEquals("81", params.invoiceTypeCode(InvoiceType.DIGITAL_VAT_SPECIAL));
        assertEquals(BaiwangJsSpec.ISSUE_V1,
                params.path("issuePath", BaiwangJsSpec.ISSUE_V2, BaiwangJsSpec.ISSUE_V1));
    }

    /** 3.1.1 工具必须显式传网关凭据，不能从空配置构造后静默签名失败。 */
    @Test
    void queryUniqueSign_requiresGatewayCredentials() {
        GatewayException ex = assertThrows(GatewayException.class,
                () -> new BaiwangJsChannelProvider().queryUniqueSign(null, "测试企业", TAX));
        assertSame(InvoiceErrorCode.CONFIG_MISSING, ex.getErrorCode());
    }

    @Test
    void resend_withoutInvoiceNo_failsFastWithoutNetwork() {
        Invoice invoice = new Invoice().setInvoiceId("INV-1").setConfigId("demo")
                .setBizOrderNo("ORDER-1").setChannelCode(BaiwangJsSpec.CODE);
        InvoiceContext ctx = InvoiceContext.of(config(), null, invoice);
        DeliveryResult r = new BaiwangJsChannelProvider().resend(ctx,
                new DeliveryRequest().setInvoice(invoice).setPhone("13800000000"));
        assertFalse(r.isAccepted());
        assertEquals("DELIVERY_NO_REQUIRED", r.getErrorCode());
    }

    @Test
    void apply_withoutTypeCode_failsFastWithoutNetwork() {
        // 无本地单据修复时，request 未给 fplxdm 就没有票种代码可用
        InvoiceContext ctx = InvoiceContext.of(config(), null, null);
        RepairResult r = new BaiwangJsChannelProvider().apply(ctx,
                new RepairRequest().setChannelCode(BaiwangJsSpec.CODE));
        assertSame(RepairResult.RepairState.FAILED, r.getState());
        assertEquals("REPAIR_PARAM_REQUIRED", r.getErrorCode());
    }

    @Test
    void query_withoutOrderNo_failsFastWithoutNetwork() {
        InvoiceContext ctx = InvoiceContext.of(config(), null, null);
        RepairResult r = new BaiwangJsChannelProvider().query(ctx, new RepairRequest());
        assertSame(RepairResult.RepairState.FAILED, r.getState());
        assertEquals("REPAIR_PARAM_REQUIRED", r.getErrorCode());
    }

    @Test
    void checkRedAvailable_requiresCredentials() {
        ChannelConfig empty = new BaiwangJsParams()
                .setConfigId("demo").setChannelCode(BaiwangJsSpec.CODE);
        Invoice original = new Invoice().setBizOrderNo("ORDER-1").setSellerTaxNo(TAX);
        InvoiceContext ctx = InvoiceContext.of(empty, null, original);
        GatewayException ex = assertThrows(GatewayException.class,
                () -> new BaiwangJsChannelProvider().checkRedAvailable(ctx));
        assertSame(InvoiceErrorCode.CONFIG_MISSING, ex.getErrorCode());
    }

    /** A 档 BW-43/52 关键语义：辅助查询失败一律降级到回调已记录的版式地址，绝不阻断主流程。 */
    @Test
    void fetch_withoutCredentials_fallsBackToCallbackAttr() {
        ChannelConfig empty = new BaiwangJsParams()
                .setConfigId("demo").setChannelCode(BaiwangJsSpec.CODE)
                .setAttr(new LinkedHashMap<String, String>());
        Map<String, String> attr = new LinkedHashMap<String, String>();
        attr.put("xmlurl", "https://cdn/x.xml");
        attr.put("pdfUrl", "https://cdn/x.pdf");
        Invoice invoice = new Invoice().setInvoiceId("INV-1").setBizOrderNo("ORDER-1")
                .setChannelCode(BaiwangJsSpec.CODE).setAttr(attr);
        InvoiceContext ctx = InvoiceContext.of(empty, null, invoice);
        List<InvoiceFile> files = new BaiwangJsChannelProvider()
                .fetch(ctx, EnumSet.of(InvoiceFileType.XML));
        assertEquals(1, files.size());
        assertSame(InvoiceFileType.XML, files.get(0).getType());
        assertEquals("https://cdn/x.xml", files.get(0).getUrl());
    }

    /** 3.3.17 hzcx 回调是补充型，绝不能被当成 fpkj 开票结果推进状态机。 */
    @Test
    void hzcxCallback_isSupplementOnlyAndNeverAdvancesState() {
        String json = "{\"ywlxdm\":\"hzcx\",\"ddlsh\":\"HZCX-1\",\"status\":\"1\","
                + "\"hzcxje\":\"100.00\",\"hzcxse\":\"6.00\",\"jshj\":\"106.00\"}";
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode(BaiwangJsSpec.CODE)
                .setBody(Base64.getEncoder().encodeToString(
                        json.getBytes(StandardCharsets.UTF_8)));
        ChannelConfig config = new ChannelConfig()
                .setAttr(new LinkedHashMap<String, String>());
        InvoiceCallbackEvent event = BaiwangJsCallbackParser.parse(raw, config);
        assertNull(event.getState(), "可冲销明细回调不迁移单据状态");
        assertEquals("100.00", event.getAttr().get("hzcxje"));
        assertEquals("6.00", event.getAttr().get("hzcxse"));
        assertTrue(event.getAttr().get("channelResult").startsWith("HZCX:"));
    }

    // ---- 夹具 ----

    private static ChannelConfig config() {
        BaiwangJsParams params = new BaiwangJsParams()
                .setGatewayKey("gw-key").setGatewaySecret("secret")
                .setQyId("Q1").setDlzh("13800000000");
        params.setConfigId("demo").setChannelCode(BaiwangJsSpec.CODE).setSellerTaxNo(TAX);
        return params;
    }
}
