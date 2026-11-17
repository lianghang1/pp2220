package io.github.lianghang1.conflux.invoice.channels.mock;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.engine.InvoiceRuntime;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端流程测试：用 mock 渠道跑通「开票 → 授权挂起 → 提交授权 → XML 归档 →
 * 红字申请 → 确认 → 开红票」，并验证异步、失败、幂等、法定原件、回调等分支。
 *
 * @author lianghang
 */
class MockInvoiceFlowTest {

    private static final String TAX = "91310000MOCK000001";
    private static final String APP_KEY = "mock-app-key";

    @Test
    void fullFlowFromAuthToRedInvoice() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());

        InvoiceResult issued = gateway.issue(request("B1"));
        assertSame(InvoiceState.WAITING_AUTH, issued.getState());
        assertNotNull(issued.getAuthChallenge());
        String sessionId = issued.getAuthChallenge().getSessionId();

        InvoiceResult authed = gateway.submitAuth(
                new AuthSubmission().setSessionId(sessionId).setCaptcha("888888"));
        assertSame(InvoiceState.SUCCEEDED, authed.getState());
        assertTrue(authed.hasLegalXml(), "开票成功必须归档 XML 法定原件");
        assertEquals(3, gateway.files(authed.getInvoiceId()).size());
        assertNotNull(gateway.findById(authed.getInvoiceId()).getInvoiceNo());

        // 幂等重入：相同业务单号直接返回原单据，不重复开票
        InvoiceResult again = gateway.issue(request("B1"));
        assertEquals(authed.getInvoiceId(), again.getInvoiceId());
        assertSame(InvoiceState.SUCCEEDED, again.getState());

        // 两段红冲
        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("R1")
                .setOriginalInvoiceId(authed.getInvoiceId()).setReason("开票信息有误"));
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
        assertNotNull(apply.getRedConfirmNo());
        assertSame(InvoiceState.RED_APPLYING,
                gateway.findById(authed.getInvoiceId()).getState());

        RedApply confirmed = gateway.queryRedApply(apply.getRedApplyId());
        assertSame(RedApplyState.CONFIRMED, confirmed.getState());
        assertSame(InvoiceState.RED_APPLY_CONFIRMED,
                gateway.findById(authed.getInvoiceId()).getState());

        InvoiceResult red = gateway.issueRed(new RedIssueRequest()
                .setAppId("demo").setRedApplyId(apply.getRedApplyId()));
        assertSame(InvoiceState.SUCCEEDED, red.getState());
        assertTrue(red.hasLegalXml());
        assertSame(InvoiceState.RED_SUCCEEDED,
                gateway.findById(authed.getInvoiceId()).getState());
        assertSame(RedApplyState.ISSUED,
                gateway.findRedApply(apply.getRedApplyId()).getState());
    }

    @Test
    void asyncIssueCanBePolledToSucceeded() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("ASYNC"));
        InvoiceResult accepted = gateway.issue(request("B2"));
        assertSame(InvoiceState.PROCESSING, accepted.getState());
        assertTrue(accepted.isAsyncAccepted());
        assertEquals(0, gateway.files(accepted.getInvoiceId()).size());

        InvoiceResult polled = gateway.query(new InvoiceQueryRequest()
                .setInvoiceId(accepted.getInvoiceId()));
        assertSame(InvoiceState.SUCCEEDED, polled.getState());
        assertTrue(polled.hasLegalXml());
    }

    @Test
    void failedIssueDoesNotArchiveFiles() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("FAIL"));
        InvoiceResult result = gateway.issue(request("B3"));
        assertSame(InvoiceState.FAILED, result.getState());
        assertEquals("MOCK_FAIL", result.getErrorCode());
        assertTrue(gateway.files(result.getInvoiceId()).isEmpty());
    }

    @Test
    void issueRedBeforeConfirmationIsRejected() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("SUCCESS"));
        InvoiceResult issued = gateway.issue(request("B4"));
        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("R4")
                .setOriginalInvoiceId(issued.getInvoiceId()).setReason("误开"));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> gateway.issueRed(new RedIssueRequest()
                        .setAppId("demo").setRedApplyId(apply.getRedApplyId())));
        assertSame(InvoiceErrorCode.RED_CONFIRM_PENDING, ex.getErrorCode());
    }

    @Test
    void idempotentReissueWithDifferentAmountIsRejected() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("SUCCESS"));
        gateway.issue(request("B5"));
        InvoiceRequest changed = request("B5")
                .setAmount(new BigDecimal("200.00"))
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("技术服务费").setAmount(new BigDecimal("200.00"))));
        GatewayException ex = assertThrows(GatewayException.class, () -> gateway.issue(changed));
        assertSame(InvoiceErrorCode.DUPLICATE_BIZ_ORDER, ex.getErrorCode());
    }

    @Test
    void successWithoutLegalXmlIsRejected() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new NoXmlProvider());
        String accountKey = InvoiceAccountKey.of(APP_KEY, TAX);
        ChannelConfig config = new ChannelConfig().setAppId("demo")
                .setChannelCode("noxml").setAccountKey(accountKey)
                .setSellerTaxNo(TAX).setParams(new MockInvoiceParams());
        ChannelInstance instance = new ChannelInstance().setInstanceId("i")
                .setAppId("demo").setChannelCode("noxml").setAccountKey(accountKey)
                .setSellerTaxNo(TAX).setTier(ChannelTier.SERVICE_API)
                .setEnabled(true).setConfig(config);
        InMemoryInvoiceConfigProvider provider = new InMemoryInvoiceConfigProvider()
                .register(config, instance);
        InvoiceGateway gateway = new InvoiceGateway(new InvoiceRuntime(registry, provider,
                new InMemoryInvoiceStore(), new InMemoryRedApplyStore(),
                new InMemoryAuthSessionStore(), new InMemoryInvoiceFileStore()));

        GatewayException ex = assertThrows(GatewayException.class, () -> gateway.issue(request("B6")));
        assertSame(InvoiceErrorCode.XML_ORIGINAL_REQUIRED, ex.getErrorCode());
    }

    @Test
    void callbackAdvancesInvoiceStateAndArchivesXml() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());
        InvoiceResult issued = gateway.issue(request("B7"));   // WAITING_AUTH

        Map<String, String> query = new HashMap<String, String>();
        query.put(InvoiceAccountKey.ROUTING_PARAM, InvoiceAccountKey.of(APP_KEY, TAX));
        String body = "{\"invoiceId\":\"" + issued.getInvoiceId()
                + "\",\"state\":\"SUCCEEDED\",\"invoiceNo\":\"FP-CALLBACK\","
                + "\"xml\":\"<EInvoice>callback</EInvoice>\"}";
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode("mock").setQueryParams(query).setBody(body);

        InvoiceResult result = gateway.handleCallback(raw);
        assertNotNull(result);
        assertSame(InvoiceState.SUCCEEDED, result.getState());
        assertTrue(result.hasLegalXml());
        assertSame(InvoiceFileType.XML, gateway.files(result.getInvoiceId()).get(0).getType());
    }

    // ---- helpers ----

    private InvoiceGateway gateway(MockInvoiceParams params) {
        params.setChannelAppId(APP_KEY).setSellerTaxNo(TAX);
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new MockInvoiceChannelProvider());
        String accountKey = InvoiceAccountKey.of(APP_KEY, TAX);
        ChannelConfig config = new ChannelConfig()
                .setAppId("demo").setChannelCode("mock")
                .setChannelInstanceId("demo::mock::" + TAX)
                .setAccountKey(accountKey).setSellerTaxNo(TAX).setParams(params);
        ChannelInstance instance = new ChannelInstance()
                .setInstanceId("instance-1").setAppId("demo").setChannelCode("mock")
                .setAccountKey(accountKey).setSellerTaxNo(TAX)
                .setTier(ChannelTier.SERVICE_API).setPriority(10)
                .setEnabled(true).setConfig(config);
        InMemoryInvoiceConfigProvider provider = new InMemoryInvoiceConfigProvider()
                .register(config, instance);
        return new InvoiceGateway(new InvoiceRuntime(registry, provider,
                new InMemoryInvoiceStore(), new InMemoryRedApplyStore(),
                new InMemoryAuthSessionStore(), new InMemoryInvoiceFileStore()));
    }

    private InvoiceRequest request(String bizOrderNo) {
        return new InvoiceRequest()
                .setAppId("demo").setBizOrderNo(bizOrderNo)
                .setSellerTaxNo(TAX).setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("技术服务费").setAmount(new BigDecimal("100.00"))))
                .setAmount(new BigDecimal("100.00"))
                .setTaxAmount(new BigDecimal("6.00"))
                .setAsyncAllowed(true);
    }

    /** 返回成功但不含 XML 原件的渠道，用于验证法定原件强约束。 */
    private static final class NoXmlProvider implements InvoiceChannelProvider {
        @Override
        public String channelCode() {
            return "noxml";
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
        public InvoiceIssuer issuer() {
            return ctx -> InvoiceResult.succeeded("REQ-NOXML", "FP-NOXML",
                    "DM", LocalDate.now(), Collections.<InvoiceFile>emptyList());
        }

        @Override
        public InvoiceFileFetcher fileFetcher() {
            return (ctx, types) -> Collections.<InvoiceFile>emptyList();
        }
    }
}
