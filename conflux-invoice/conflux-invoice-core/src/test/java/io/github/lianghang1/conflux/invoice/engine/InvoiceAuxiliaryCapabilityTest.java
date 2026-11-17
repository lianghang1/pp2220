package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.api.RedAvailableResult;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceDeliverer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRepairer;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import io.github.lianghang1.conflux.invoice.support.InMemoryAuthSessionStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceFileStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryRedApplyStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架层 A+B 档扩展单测：新值对象、可选 SPI 的 default-null 契约、
 * 注册表能力查找、以及门面/编排层新增动作（交付、修复、主动确认、红冲前置校验、按票号查单）。
 *
 * <p>全部走内存 Store 与桩渠道，不发网络请求。</p>
 *
 * @author lianghang
 */
class InvoiceAuxiliaryCapabilityTest {

    private static final String CODE = "stub";
    private static final String TAX = "91310000TEST000001";
    private static final String ACCOUNT_KEY = InvoiceAccountKey.of("acc", TAX);
    private static final String FPHM = "24312000000000000001";
    private static final String FPDM = "144031609110";

    // ==================== 新值对象 ====================

    @Test
    void deliveryResult_factoriesSetExpectedFlags() {
        assertTrue(DeliveryResult.ok().isAccepted());
        assertFalse(DeliveryResult.ok().isAsyncAccepted());
        DeliveryResult async = DeliveryResult.acceptedAsync("CH-1");
        assertTrue(async.isAccepted());
        assertTrue(async.isAsyncAccepted());
        assertEquals("CH-1", async.getChannelOrderNo());
        DeliveryResult failed = DeliveryResult.failed("E1", "失败");
        assertFalse(failed.isAccepted());
        assertEquals("E1", failed.getErrorCode());
        assertEquals("失败", failed.getErrorMsg());
    }

    @Test
    void repairResult_factoriesSetExpectedStateAndFiles() {
        assertSame(RepairResult.RepairState.PROCESSING, new RepairResult().getState());
        RepairResult accepted = RepairResult.accepted("CH-1");
        assertSame(RepairResult.RepairState.PROCESSING, accepted.getState());
        assertEquals("CH-1", accepted.getChannelOrderNo());
        RepairResult succeeded = RepairResult.succeeded("CH-2", null);
        assertSame(RepairResult.RepairState.SUCCEEDED, succeeded.getState());
        assertTrue(succeeded.getFiles().isEmpty());
        RepairResult failed = RepairResult.failed("E2", "未修复");
        assertSame(RepairResult.RepairState.FAILED, failed.getState());
        assertEquals("E2", failed.getErrorCode());
    }

    @Test
    void newRequests_defaultsAndChainableSetters() {
        RedConfirmRequest confirm = new RedConfirmRequest()
                .setRedApplyId("RA-1").setAccept(true);
        assertEquals("RA-1", confirm.getRedApplyId());
        assertTrue(confirm.isAccept());
        RedAvailableResult available = new RedAvailableResult();
        assertTrue(available.isAvailable());
        assertFalse(available.isAsyncAccepted());
        DeliveryRequest delivery = new DeliveryRequest()
                .setInvoiceId("INV-1").setEmail("a@b.com");
        assertEquals("INV-1", delivery.getInvoiceId());
        RepairRequest repair = new RepairRequest()
                .setChannelCode(CODE).setSellerTaxNo(TAX);
        assertEquals(CODE, repair.getChannelCode());
        assertEquals(TAX, repair.getSellerTaxNo());
        InvoiceQueryRequest query = new InvoiceQueryRequest()
                .setInvoiceNo(FPHM).setInvoiceCode(FPDM);
        assertEquals(FPHM, query.getInvoiceNo());
        assertEquals(FPDM, query.getInvoiceCode());
    }

    // ==================== 可选 SPI 与注册表 ====================

    /** 新增能力必须 default null，否则 mock 等其他渠道会被迫实现。 */
    @Test
    void optionalCapabilitiesDefaultToNull() {
        InvoiceChannelProvider p = new InvoiceChannelProvider() {
            @Override
            public String channelCode() {
                return "plain";
            }

            @Override
            public ChannelTier channelTier() {
                return ChannelTier.SERVICE_API;
            }

            @Override
            public Set<InvoiceType> supportedTypes() {
                return EnumSet.noneOf(InvoiceType.class);
            }
        };
        assertNull(p.deliverer());
        assertNull(p.repairer());

        InvoiceRedHandler red = new InvoiceRedHandler() {
            @Override
            public RedApplyResult apply(InvoiceContext ctx) {
                return null;
            }

            @Override
            public RedApplyResult queryRed(InvoiceContext ctx) {
                return null;
            }

            @Override
            public InvoiceResult issueRed(InvoiceContext ctx) {
                return null;
            }
        };
        assertNull(red.checkRedAvailable(null));
        assertNull(red.confirm(null, new RedConfirmRequest()));
    }

    @Test
    void registryNewCapabilities_missingThrows_orNullReturnsNull() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new StubProvider());
        GatewayException ex1 = assertThrows(GatewayException.class,
                () -> registry.deliverer(CODE));
        assertSame(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED, ex1.getErrorCode());
        GatewayException ex2 = assertThrows(GatewayException.class,
                () -> registry.repairer(CODE));
        assertSame(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED, ex2.getErrorCode());
        assertNull(registry.delivererOrNull(CODE));
        assertNull(registry.repairerOrNull(CODE));
    }

    // ==================== 门面 / 编排层新增动作 ====================

    @Test
    void resend_validatesDeliveryTarget() {
        fixture();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.resend(new DeliveryRequest().setInvoiceId("INV-1")));
        assertSame(InvoiceErrorCode.DELIVERY_TARGET_REQUIRED, ex.getErrorCode());
    }

    @Test
    void resend_missingInvoiceThrowsNotFound() {
        fixture();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.resend(new DeliveryRequest()
                        .setInvoiceId("NOPE").setPhone("13800000000")));
        assertSame(InvoiceErrorCode.INVOICE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void resend_delegatesToDelivererWithoutChangingState() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        provider.setDeliverer((ctx, req) -> DeliveryResult.ok());
        DeliveryResult r = gateway.resend(new DeliveryRequest()
                .setInvoiceId("INV-1").setEmail("a@b.com"));
        assertTrue(r.isAccepted());
        assertSame(InvoiceState.SUCCEEDED, gateway.findById("INV-1").getState());
    }

    @Test
    void repair_withoutLocalInvoiceRequiresChannelCode() {
        fixture();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.repair(new RepairRequest()
                        .setAppId("demo").setSellerTaxNo(TAX)));
        assertSame(InvoiceErrorCode.REPAIR_PARAM_REQUIRED, ex.getErrorCode());
    }

    @Test
    void repair_delegatesToRepairer() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        provider.setRepairer(new InvoiceRepairer() {
            @Override
            public RepairResult apply(InvoiceContext ctx, RepairRequest req) {
                return RepairResult.accepted("REPAIR-1");
            }

            @Override
            public RepairResult query(InvoiceContext ctx, RepairRequest req) {
                return RepairResult.accepted("REPAIR-1");
            }
        });
        RepairResult r = gateway.repair(new RepairRequest().setInvoiceId("INV-1"));
        assertSame(RepairResult.RepairState.PROCESSING, r.getState());
        assertEquals("REPAIR-1", r.getChannelOrderNo());
    }

    @Test
    void queryRepair_requiresChannelOrderNo() {
        fixture();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.queryRepair(new RepairRequest().setInvoiceId("INV-1")));
        assertSame(InvoiceErrorCode.REPAIR_PARAM_REQUIRED, ex.getErrorCode());
    }

    /** 渠道未实现主动确认/拒绝时，不静默降级为查状态。 */
    @Test
    void confirmRedAction_unsupportedThrowsCapabilityError() {
        fixture();
        RedApply apply = saveRedApply(RedApplyState.WAITING_CONFIRM);
        provider.setRedHandler(new StubRedHandler());
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.confirmRed(new RedConfirmRequest()
                        .setRedApplyId(apply.getRedApplyId()).setAccept(true)));
        assertSame(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    void confirmRedAction_appliesConfirmedResult() {
        fixture();
        RedApply apply = saveRedApply(RedApplyState.WAITING_CONFIRM);
        StubRedHandler red = new StubRedHandler();
        red.confirmResult = RedApplyResult.confirmed("HZ001");
        provider.setRedHandler(red);
        RedApply after = gateway.confirmRed(new RedConfirmRequest()
                .setRedApplyId(apply.getRedApplyId()).setAccept(true));
        assertSame(RedApplyState.CONFIRMED, after.getState());
        assertEquals("HZ001", after.getRedConfirmNo());
    }

    /** 渠道明确返回「不可冲」时阻断红冲申请。 */
    @Test
    void applyRed_blockedWhenRedNotAvailable() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        StubRedHandler red = new StubRedHandler();
        red.available = new RedAvailableResult()
                .setAvailable(false).setErrorMsg("可红冲余额不足");
        provider.setRedHandler(red);
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.applyRed(redRequest("BIZ-RED-2")));
        assertSame(InvoiceErrorCode.RED_NOT_AVAILABLE, ex.getErrorCode());
    }

    @Test
    void applyRed_allowsWhenCheckUnsupported() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        provider.setRedHandler(new StubRedHandler());
        RedApply apply = gateway.applyRed(redRequest("BIZ-RED-3"));
        assertNotNull(apply);
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
        assertSame(InvoiceState.RED_APPLYING, gateway.findById("INV-1").getState());
    }

    @Test
    void applyRed_allowsWhenCheckAsyncAccepted() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        StubRedHandler red = new StubRedHandler();
        red.available = new RedAvailableResult().setAsyncAccepted(true).setAvailable(false);
        provider.setRedHandler(red);
        RedApply apply = gateway.applyRed(redRequest("BIZ-RED-4"));
        assertNotNull(apply);
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
    }

    /** 快捷红冲：渠道免确认时自动接续 issueRed，一步到位。 */
    @Test
    void quickRed_autoIssuesWhenConfirmed() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        StubRedHandler red = new StubRedHandler();
        red.applyResult = RedApplyResult.confirmed("HZ001");
        provider.setRedHandler(red);

        QuickRedResult result = gateway.quickRed(redRequest("BIZ-RED-Q1"));
        assertNotNull(result);
        assertTrue(result.isAccepted());
        assertFalse(result.isWaitingConfirm());
        assertNotNull(result.getRedApply());
        assertNotNull(result.getRedInvoice(), "ACCEPTED 应携带红票受理结果");
        assertEquals(1, red.issueRedCalls);
        assertSame(InvoiceState.RED_APPLYING, gateway.findById("INV-1").getState());
        Invoice redInvoice = invoiceStore.findByBizOrderNo("demo", "BIZ-RED-Q1-RED");
        assertNotNull(redInvoice, "自动开票后应建立红字发票单据");
        assertSame(InvoiceState.PROCESSING, redInvoice.getState());
    }

    /** 快捷红冲：需对方确认时不自动开票，返回确认单由使用方后续处理。 */
    @Test
    void quickRed_returnsWaitingWhenConfirmNeeded() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        StubRedHandler red = new StubRedHandler();
        red.applyResult = RedApplyResult.waiting("RED-1", LocalDateTime.now().plusHours(72L));
        provider.setRedHandler(red);

        QuickRedResult result = gateway.quickRed(redRequest("BIZ-RED-Q2"));
        assertNotNull(result);
        assertTrue(result.isWaitingConfirm());
        assertFalse(result.isAccepted());
        assertNotNull(result.getRedApply());
        assertSame(RedApplyState.WAITING_CONFIRM, result.getRedApply().getState());
        assertNull(result.getRedInvoice(), "待确认时不应有红票结果");
        assertEquals(0, red.issueRedCalls);
    }

    /** 快捷红冲：复用 applyRed 的原票状态校验，非成功蓝票拒绝。 */
    @Test
    void quickRed_rejectsNonSucceededInvoice() {
        fixture();
        saveInvoice("INV-1", InvoiceState.PROCESSING, FPHM, FPDM);
        provider.setRedHandler(new StubRedHandler());

        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.quickRed(redRequest("BIZ-RED-Q3")));
        assertSame(InvoiceErrorCode.STATE_ILLEGAL, ex.getErrorCode());
    }

    /** BW-50：查单支持按票号定位本地单据，复用 Store.findByInvoiceNo。 */
    @Test
    void query_locatesByInvoiceNo() {
        fixture();
        saveInvoice("INV-1", InvoiceState.SUCCEEDED, FPHM, FPDM);
        InvoiceResult r = gateway.query(new InvoiceQueryRequest()
                .setAppId("demo").setInvoiceNo(FPHM).setInvoiceCode(FPDM));
        assertNotNull(r);
        assertEquals(FPHM, r.getInvoiceNo());
        assertSame(InvoiceState.SUCCEEDED, r.getState());
    }

    // ==================== 夹具 ====================

    private StubProvider provider;
    private InMemoryInvoiceStore invoiceStore;
    private InMemoryRedApplyStore redApplyStore;
    private InvoiceGateway gateway;

    private void fixture() {
        provider = new StubProvider();
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(provider);

        ChannelConfig config = new ChannelConfig()
                .setAppId("demo").setChannelCode(CODE)
                .setChannelInstanceId("demo::stub::" + TAX)
                .setAccountKey(ACCOUNT_KEY).setSellerTaxNo(TAX);
        ChannelInstance instance = new ChannelInstance()
                .setInstanceId("i1").setAppId("demo").setChannelCode(CODE)
                .setAccountKey(ACCOUNT_KEY).setSellerTaxNo(TAX)
                .setTier(ChannelTier.SERVICE_API).setPriority(10).setEnabled(true)
                .setConfig(config);
        InMemoryInvoiceConfigProvider configProvider = new InMemoryInvoiceConfigProvider()
                .register(config, instance);

        invoiceStore = new InMemoryInvoiceStore();
        redApplyStore = new InMemoryRedApplyStore();
        gateway = new InvoiceGateway(new InvoiceRuntime(registry, configProvider,
                invoiceStore, redApplyStore,
                new InMemoryAuthSessionStore(), new InMemoryInvoiceFileStore()));
    }

    private void saveInvoice(String id, InvoiceState state, String invoiceNo, String invoiceCode) {
        LocalDateTime now = LocalDateTime.now();
        invoiceStore.save(new Invoice()
                .setInvoiceId(id).setAppId("demo").setBizOrderNo("BIZ-" + id)
                .setChannelOrderNo("CH-" + id).setChannelCode(CODE)
                .setChannelInstanceId("i1").setAccountKey(ACCOUNT_KEY)
                .setSellerTaxNo(TAX).setSellerName("测试销方")
                .setBuyerTitle("测试购方").setBuyerTaxNo("91310000BUYER00001")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setAmount(new BigDecimal("100.00"))
                .setTaxAmount(new BigDecimal("6.00"))
                .setInvoiceNo(invoiceNo).setInvoiceCode(invoiceCode)
                .setState(state).setCreatedTime(now).setUpdatedTime(now).setVersion(0L));
    }

    private RedApply saveRedApply(RedApplyState state) {
        LocalDateTime now = LocalDateTime.now();
        RedApply apply = new RedApply()
                .setRedApplyId("RA-1").setAppId("demo").setBizRedNo("BIZ-RED-1")
                .setOriginalInvoiceId("NO-SUCH-INVOICE")
                .setOriginalInvoiceNo(FPHM).setOriginalInvoiceCode(FPDM)
                .setReason("退货").setAmount(new BigDecimal("100.00"))
                .setChannelCode(CODE).setAccountKey(ACCOUNT_KEY)
                .setState(state).setVersion(0L)
                .setCreatedTime(now).setUpdatedTime(now);
        redApplyStore.save(apply);
        return apply;
    }

    private static RedApplyRequest redRequest(String bizRedNo) {
        return new RedApplyRequest().setAppId("demo").setBizRedNo(bizRedNo)
                .setReason("退货").setOriginalInvoiceNo(FPHM).setOriginalInvoiceCode(FPDM)
                .setAmount(new BigDecimal("100.00"));
    }

    /** 可配置能力的桩渠道；未设置的能力保持 null，用于验证 default-null 契约。 */
    private static final class StubProvider implements InvoiceChannelProvider {
        private InvoiceDeliverer deliverer;
        private InvoiceRepairer repairer;
        private InvoiceRedHandler redHandler;

        void setDeliverer(InvoiceDeliverer deliverer) {
            this.deliverer = deliverer;
        }

        void setRepairer(InvoiceRepairer repairer) {
            this.repairer = repairer;
        }

        void setRedHandler(InvoiceRedHandler redHandler) {
            this.redHandler = redHandler;
        }

        @Override
        public String channelCode() {
            return CODE;
        }

        @Override
        public ChannelTier channelTier() {
            return ChannelTier.SERVICE_API;
        }

        @Override
        public Set<InvoiceType> supportedTypes() {
            return EnumSet.allOf(InvoiceType.class);
        }

        @Override
        public InvoiceDeliverer deliverer() {
            return deliverer;
        }

        @Override
        public InvoiceRepairer repairer() {
            return repairer;
        }

        @Override
        public InvoiceRedHandler redHandler() {
            return redHandler;
        }
    }

    /** 红字桩：available / confirmResult 为 null 即表示渠道不支持对应可选能力。 */
    private static final class StubRedHandler implements InvoiceRedHandler {
        private RedAvailableResult available;
        private RedApplyResult confirmResult;
        private RedApplyResult applyResult;
        private int issueRedCalls;

        @Override
        public RedApplyResult apply(InvoiceContext ctx) {
            return applyResult != null ? applyResult
                    : RedApplyResult.waiting("RED-1", LocalDateTime.now().plusHours(72L));
        }

        @Override
        public RedApplyResult queryRed(InvoiceContext ctx) {
            return RedApplyResult.confirmed("HZ001");
        }

        @Override
        public InvoiceResult issueRed(InvoiceContext ctx) {
            issueRedCalls++;
            return InvoiceResult.accepted("RED-1", true);
        }

        @Override
        public RedAvailableResult checkRedAvailable(InvoiceContext ctx) {
            return available;
        }

        @Override
        public RedApplyResult confirm(InvoiceContext ctx, RedConfirmRequest request) {
            return confirmResult;
        }
    }
}
