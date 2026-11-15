package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.engine.InvoiceRuntime;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.support.InMemoryAuthSessionStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceFileStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryRedApplyStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 百旺金穗云双回调编排层端到端测试：Base64 回调 → BaiwangJsCallbackParser → InvoiceOrchestrator。
 *
 * <p>平台把「票号/结果」({@code fpkj}) 与「版式文件」({@code qdbswj}) 拆成两次独立通知，
 * 且先后顺序不固定。本测试验证编排层的「补充型回调」语义：先到的票号只合并字段、不迁移状态，
 * 后到的版式回调置 {@code SUCCEEDED} 且不抹掉票号；反序到达同样收敛。</p>
 *
 * <p>不发任何网络请求：单据由内存 Store 直接预置为 {@code PROCESSING}，
 * 只走渠道 {@code callbackHandler()} 与编排层。</p>
 *
 * @author lianghang
 */
class BaiwangJsSupplementFlowTest {

    private static final String TAX = "91310000TEST000001";
    private static final String APP_KEY = "gw-key";
    private static final String DDLSH = "ORDER-SUPPLEMENT-001";
    private static final String INVOICE_ID = "INV-SUPP-1";
    private static final String ACCOUNT_KEY = InvoiceAccountKey.of(APP_KEY, TAX);
    private static final String FPHM = "24312000000000000001";

    private static final String FACE_JSON = "{\n"
            + "  \"ywlxdm\":\"fpkj\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
            + "  \"qdfphm\":\"" + FPHM + "\", \"kprq\":\"20260115142522\",\n"
            + "  \"jshj\":\"100.00\", \"hjse\":\"6.00\"\n"
            + "}";

    private static final String LAYOUT_JSON = "{\n"
            + "  \"ywlxdm\":\"qdbswj\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
            + "  \"xmlurl\":\"https://cdn/x.xml\", \"ofdurl\":\"https://cdn/x.ofd\",\n"
            + "  \"pdf\":\"https://cdn/x.pdf\"\n"
            + "}";

    @Test
    void faceThenLayout_supplementKeepsInvoiceNoAndLayoutAdvancesToSucceeded() {
        InvoiceGateway gateway = fixture();

        InvoiceResult face = gateway.handleCallback(callback(FACE_JSON));
        assertNotNull(face, "补充型回调也应返回当前单据快照");
        assertSame(InvoiceState.PROCESSING, face.getState(),
                "fpkj 成功只补票面字段，不迁移状态");
        assertEquals(FPHM, face.getInvoiceNo());
        assertEquals(LocalDate.of(2026, 1, 15), face.getInvoiceDate());
        assertTrue(gateway.files(INVOICE_ID).isEmpty(),
                "补充型回调此时还没有版式地址，不应归档文件");

        InvoiceResult layout = gateway.handleCallback(callback(LAYOUT_JSON));
        assertNotNull(layout);
        assertSame(InvoiceState.SUCCEEDED, layout.getState());
        assertEquals(FPHM, layout.getInvoiceNo(), "版式回调不能把已补充的票号抹成 null");
        assertTrue(layout.hasLegalXml(), "qdbswj 必须归档 XML 法定原件");
        assertEquals(3, gateway.files(INVOICE_ID).size());

        Invoice persisted = gateway.findById(INVOICE_ID);
        assertEquals(FPHM, persisted.getInvoiceNo());
        assertSame(InvoiceState.SUCCEEDED, persisted.getState());
        assertTrue(persisted.getVersion() >= 1L, "补充型回调也走 CAS 写入");
    }

    @Test
    void layoutThenFace_alsoConvergesAndDoesNotRollbackState() {
        InvoiceGateway gateway = fixture();

        InvoiceResult layout = gateway.handleCallback(callback(LAYOUT_JSON));
        assertNotNull(layout);
        assertSame(InvoiceState.SUCCEEDED, layout.getState());
        assertTrue(layout.hasLegalXml());

        InvoiceResult face = gateway.handleCallback(callback(FACE_JSON));
        assertNotNull(face);
        assertSame(InvoiceState.SUCCEEDED, face.getState(),
                "票号回调后到只做字段补充，不把 SUCCEEDED 回退");
        assertEquals(FPHM, face.getInvoiceNo());
        assertTrue(hasXml(gateway), "补充后 XML 法定原件仍在");
        assertEquals(3, gateway.files(INVOICE_ID).size());

        assertEquals(FPHM, gateway.findById(INVOICE_ID).getInvoiceNo());
    }

    private static boolean hasXml(InvoiceGateway gateway) {
        for (InvoiceFile file : gateway.files(INVOICE_ID)) {
            if (file.getType() == InvoiceFileType.XML) {
                return true;
            }
        }
        return false;
    }

    // ---- 夹具 ----

    private InvoiceCallbackRequest callback(String json) {
        Map<String, String> query = new HashMap<String, String>();
        query.put(InvoiceAccountKey.ROUTING_PARAM, ACCOUNT_KEY);
        return new InvoiceCallbackRequest()
                .setChannelCode(BaiwangJsSpec.CODE)
                .setQueryParams(query)
                .setBody(Base64.getEncoder().encodeToString(
                        json.getBytes(StandardCharsets.UTF_8)));
    }

    private InvoiceGateway fixture() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new BaiwangJsChannelProvider());

        ChannelConfig config = new ChannelConfig()
                .setAppId("demo")
                .setChannelCode(BaiwangJsSpec.CODE)
                .setChannelInstanceId("demo::baiwangjs::" + TAX)
                .setAccountKey(ACCOUNT_KEY)
                .setSellerTaxNo(TAX)
                .setParams(new BaiwangJsParams()
                        .setGatewayKey(APP_KEY).setGatewaySecret("secret").setQyId("Q1"));
        ChannelInstance instance = new ChannelInstance()
                .setInstanceId("instance-1").setAppId("demo")
                .setChannelCode(BaiwangJsSpec.CODE).setAccountKey(ACCOUNT_KEY)
                .setSellerTaxNo(TAX).setTier(ChannelTier.SERVICE_API)
                .setPriority(10).setEnabled(true).setConfig(config);
        InMemoryInvoiceConfigProvider configProvider = new InMemoryInvoiceConfigProvider()
                .register(config, instance);

        InMemoryInvoiceStore invoiceStore = new InMemoryInvoiceStore();
        LocalDateTime now = LocalDateTime.now();
        invoiceStore.save(new Invoice()
                .setInvoiceId(INVOICE_ID).setAppId("demo")
                .setBizOrderNo(DDLSH).setChannelOrderNo(DDLSH)
                .setChannelCode(BaiwangJsSpec.CODE).setSellerTaxNo(TAX)
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setAmount(new BigDecimal("100.00"))
                .setTaxAmount(new BigDecimal("6.00"))
                .setState(InvoiceState.PROCESSING)
                .setCreatedTime(now).setUpdatedTime(now).setVersion(0L));

        return new InvoiceGateway(new InvoiceRuntime(
                registry, configProvider, invoiceStore, new InMemoryRedApplyStore(),
                new InMemoryAuthSessionStore(), new InMemoryInvoiceFileStore()));
    }
}
