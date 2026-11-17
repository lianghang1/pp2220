package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.model.*;
import io.github.lianghang1.conflux.invoice.support.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 红冲生命周期闭环回归：覆盖异步红票成功后的状态联动，以及快捷红冲幂等重入的结果还原。
 *
 * @author lianghang
 */
class RedIssueLifecycleTest {
    private static final String CODE = "stub";
    private static final String TAX = "91310000TEST000001";
    private static final String AK = InvoiceAccountKey.of("acc", TAX);

    static class P implements InvoiceChannelProvider {
        InvoiceIssuer issuer;
        InvoiceQuerier querier;
        InvoiceRedHandler red;

        public String channelCode() { return CODE; }
        public ChannelTier channelTier() { return ChannelTier.SERVICE_API; }
        public Set<InvoiceType> supportedTypes() { return EnumSet.allOf(InvoiceType.class); }
        public InvoiceIssuer issuer() { return issuer; }
        public InvoiceQuerier querier() { return querier; }
        public InvoiceRedHandler redHandler() { return red; }
    }

    private InvoiceGateway gw(P p, StoreHolder h) {
        InvoiceChannelRegistry reg = new InvoiceChannelRegistry();
        reg.register(p);
        ChannelConfig c = new ChannelConfig().setAppId("demo").setChannelCode(CODE)
                .setChannelInstanceId("i").setAccountKey(AK).setSellerTaxNo(TAX);
        ChannelInstance ci = new ChannelInstance().setInstanceId("i").setAppId("demo")
                .setChannelCode(CODE).setAccountKey(AK).setSellerTaxNo(TAX)
                .setTier(ChannelTier.SERVICE_API).setPriority(10).setEnabled(true).setConfig(c);
        InMemoryInvoiceConfigProvider cp = new InMemoryInvoiceConfigProvider().register(c, ci);
        h.invoiceStore = new InMemoryInvoiceStore();
        h.redStore = new InMemoryRedApplyStore();
        return new InvoiceGateway(new InvoiceRuntime(reg, cp, h.invoiceStore, h.redStore,
                new InMemoryAuthSessionStore(), new InMemoryInvoiceFileStore()));
    }

    static class StoreHolder {
        InMemoryInvoiceStore invoiceStore;
        InMemoryRedApplyStore redStore;
    }

    private void saveBlue(StoreHolder h) {
        LocalDateTime now = LocalDateTime.now();
        h.invoiceStore.save(new Invoice().setInvoiceId("INV-1").setAppId("demo")
                .setBizOrderNo("B-1").setChannelOrderNo("CH-1").setChannelCode(CODE)
                .setChannelInstanceId("i").setAccountKey(AK).setSellerTaxNo(TAX)
                .setBuyerTitle("购方").setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setAmount(new BigDecimal("100.00")).setTaxAmount(new BigDecimal("6.00"))
                .setInvoiceNo("FPHM").setInvoiceCode("FPDM")
                .setState(InvoiceState.SUCCEEDED).setVersion(0L)
                .setCreatedTime(now).setUpdatedTime(now));
    }

    private List<InvoiceFile> xml() {
        return Collections.singletonList(new InvoiceFile()
                .setType(InvoiceFileType.XML).setContent("<xml>test</xml>"));
    }

    /** 渠道一步返回 PROCESSING 受理结果的快捷红冲桩（模拟百望 fastRed 只回 serialNo）。 */
    private InvoiceRedHandler asyncIssuedRed() {
        return new InvoiceRedHandler() {
            public RedApplyResult apply(InvoiceContext c) { return null; }
            public RedApplyResult queryRed(InvoiceContext c) { return null; }
            public InvoiceResult issueRed(InvoiceContext c) { return null; }
            public QuickRedOutcome quickRed(InvoiceContext c) {
                return QuickRedOutcome.issued(InvoiceResult.accepted("BW-SERIAL", true));
            }
        };
    }

    /**
     * Bug A：红票异步受理后靠查单推进到 SUCCEEDED，确认单应联动 ISSUED、原票应联动 RED_SUCCEEDED。
     * 覆盖快捷红冲留给原票的 RED_APPLYING 中间态。
     */
    @Test
    void asyncRedPollClosesLoopForApplyAndOriginal() {
        P p = new P();
        StoreHolder h = new StoreHolder();
        p.red = asyncIssuedRed();
        p.querier = ctx -> InvoiceResult.succeeded("BW-SERIAL", "RED-FPHM", "FPDM",
                LocalDate.now(), xml());
        InvoiceGateway gw = gw(p, h);
        saveBlue(h);

        QuickRedResult qr = gw.quickRed(new RedApplyRequest().setAppId("demo")
                .setBizRedNo("R1").setOriginalInvoiceId("INV-1").setReason("错开")
                .setAmount(new BigDecimal("100.00")));
        assertSame(InvoiceState.RED_APPLYING, gw.findById("INV-1").getState(),
                "快捷红冲受理后原票应处于 RED_APPLYING");

        String redId = h.invoiceStore.findByBizOrderNo("demo", "R1-RED").getInvoiceId();
        InvoiceResult polled = gw.query(new InvoiceQueryRequest().setInvoiceId(redId));
        assertSame(InvoiceState.SUCCEEDED, polled.getState(), "红票应被查单推进到成功");
        assertSame(InvoiceState.RED_SUCCEEDED, gw.findById("INV-1").getState(),
                "红票成功后原票应联动为 RED_SUCCEEDED");
        assertSame(RedApplyState.ISSUED,
                gw.findRedApply(qr.getRedApply().getRedApplyId()).getState(),
                "红票成功后确认单应联动为 ISSUED");
    }

    /** Bug A（同步路径）：issueRed 同步成功后同样联动原票与确认单。 */
    @Test
    void syncRedIssueClosesLoop() {
        P p = new P();
        StoreHolder h = new StoreHolder();
        p.red = new InvoiceRedHandler() {
            public RedApplyResult apply(InvoiceContext c) {
                return RedApplyResult.waiting("HZ-1", LocalDateTime.now().plusDays(1));
            }
            public RedApplyResult queryRed(InvoiceContext c) {
                return RedApplyResult.confirmed("HZ-1");
            }
            public InvoiceResult issueRed(InvoiceContext c) {
                return InvoiceResult.succeeded("BW-RED", "RED-FPHM", "FPDM",
                        LocalDate.now(), xml());
            }
            public QuickRedOutcome quickRed(InvoiceContext c) { return null; }
        };
        InvoiceGateway gw = gw(p, h);
        saveBlue(h);

        RedApply apply = gw.applyRed(new RedApplyRequest().setAppId("demo")
                .setBizRedNo("R3").setOriginalInvoiceId("INV-1").setReason("错开")
                .setAmount(new BigDecimal("100.00")));
        gw.queryRedApply(apply.getRedApplyId());
        InvoiceResult red = gw.issueRed(new io.github.lianghang1.conflux.invoice.api.RedIssueRequest()
                .setAppId("demo").setRedApplyId(apply.getRedApplyId()));
        assertSame(InvoiceState.SUCCEEDED, red.getState());
        assertSame(InvoiceState.RED_SUCCEEDED, gw.findById("INV-1").getState());
        assertSame(RedApplyState.ISSUED, gw.findRedApply(apply.getRedApplyId()).getState());
    }

    /** 确认单已 ISSUED 但原票卡在 RED_APPLYING 时，红票成功回调重入应补齐原票联动。 */
    @Test
    void reentryRepairsOriginalWhenApplyAlreadyIssued() {
        P p = new P();
        StoreHolder h = new StoreHolder();
        p.red = asyncIssuedRed();
        p.querier = ctx -> InvoiceResult.succeeded("BW-SERIAL", "RED-FPHM", "FPDM",
                LocalDate.now(), xml());
        InvoiceGateway gw = gw(p, h);
        saveBlue(h);

        QuickRedResult qr = gw.quickRed(new RedApplyRequest().setAppId("demo")
                .setBizRedNo("R4").setOriginalInvoiceId("INV-1").setReason("错开")
                .setAmount(new BigDecimal("100.00")));
        String applyId = qr.getRedApply().getRedApplyId();
        // 模拟历史中间态：确认单已 ISSUED，但原票仍停在 RED_APPLYING
        RedApply apply = h.redStore.findById(applyId);
        long ver = apply.getVersion();
        apply.setState(RedApplyState.ISSUED).setVersion(ver + 1);
        h.redStore.tryUpdate(apply, ver);

        String redId = h.invoiceStore.findByBizOrderNo("demo", "R4-RED").getInvoiceId();
        gw.query(new InvoiceQueryRequest().setInvoiceId(redId));
        assertSame(InvoiceState.RED_SUCCEEDED, gw.findById("INV-1").getState(),
                "确认单已 ISSUED 时仍应补齐原票联动");
    }

    /**
     * Bug B：quickRed 幂等重入时，尽管确认单尚未回写 redInvoiceId（红票在途），
     * 仍应通过红票业务单号反查到已建单据并还原 accepted 结果。
     */
    @Test
    void quickRedIdempotentReentryKeepsRedInvoice() {
        P p = new P();
        StoreHolder h = new StoreHolder();
        p.red = asyncIssuedRed();
        InvoiceGateway gw = gw(p, h);
        saveBlue(h);
        RedApplyRequest req = new RedApplyRequest().setAppId("demo").setBizRedNo("R2")
                .setOriginalInvoiceId("INV-1").setReason("错开")
                .setAmount(new BigDecimal("100.00"));

        QuickRedResult first = gw.quickRed(req);
        assertTrue(first.isAccepted());
        assertNotNull(first.getRedInvoice(), "首次受理应返回 PROCESSING 的红票单据");

        QuickRedResult second = gw.quickRed(req);
        assertTrue(second.isAccepted());
        assertNotNull(second.getRedInvoice(), "幂等重入应还原已建红票单据，不能为 null");
        assertEquals(first.getRedInvoice().getInvoiceId(), second.getRedInvoice().getInvoiceId(),
                "重入应返回同一张红票单据");
        assertNotNull(h.invoiceStore.findByBizOrderNo("demo", "R2-RED"),
                "红票单据已建单");
    }
}
