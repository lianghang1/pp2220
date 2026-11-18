package io.github.lianghang1.conflux.invoice.channels.mock;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyQueryRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceConfigProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端流程测试（SDK 无台账）：验证「组装载体 → 调渠道 → 归一返回」与载体往返。
 *
 * @author lianghang
 */
class MockInvoiceFlowTest {
    private static final String TAX = "91310000MOCK000001";
    private static final String APP_KEY = "mock-app-key";

    /**
     * 蓝票开票 → 授权挂起 → 提交授权 → XML 原件 → 两段红冲。
     * 每一步的载体由使用方保管并回传，SDK 不持有任何台账。
     */
    @Test
    void fullFlowFromAuthToRedInvoiceViaCarrierRoundTrip() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());
        InvoiceResult issued = gateway.issue(request("B1"));
        assertSame(InvoiceState.WAITING_AUTH, issued.getState());
        assertNotNull(issued.getAuthChallenge());
        assertNotNull(issued.getInvoice(), "开票结果应回传发票载体供使用方保管");
        assertNotNull(issued.getAuthSession(), "WAITING_AUTH 应回传授权会话载体");

        // 授权提交：会话 + 发票载体回传
        InvoiceResult authed = gateway.submitAuth(new AuthSubmission()
                .setAppId("demo")
                .setSession(issued.getAuthSession())
                .setInvoice(issued.getInvoice())
                .setCaptcha("888888"));
        assertSame(InvoiceState.SUCCEEDED, authed.getState());
        assertTrue(authed.hasLegalXml(), "开票成功必须取得 XML 法定原件");
        assertEquals(3, authed.getFiles().size());
        Invoice blueInvoice = authed.getInvoice();
        assertNotNull(blueInvoice.getInvoiceNo());

        // 两段红冲第一段：原票载体回传
        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("R1")
                .setOriginalInvoice(blueInvoice).setReason("开票信息有误"));
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
        assertNotNull(apply.getRedConfirmNo());
        assertNotNull(apply.getChannelOrderNo(), "红冲申请应拿到渠道单号");

        // 查询确认状态：确认单载体回传
        RedApply confirmed = gateway.queryRedApply(new RedApplyQueryRequest()
                .setAppId("demo").setRedApply(apply));
        assertSame(RedApplyState.CONFIRMED, confirmed.getState());

        // 第二段开具红票：确认单 + 原票载体回传
        InvoiceResult red = gateway.issueRed(new RedIssueRequest()
                .setAppId("demo").setRedApply(confirmed).setOriginalInvoice(blueInvoice));
        assertSame(InvoiceState.SUCCEEDED, red.getState());
        assertTrue(red.hasLegalXml());
        assertEquals("true", red.getInvoice().getAttrs().get("RED_INVOICE"));
    }

    /** 异步开票 → 查单拿快照（状态推进由使用方负责）。 */
    @Test
    void asyncIssueCanBePolledToSucceeded() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("ASYNC"));
        InvoiceResult accepted = gateway.issue(request("B2"));
        assertSame(InvoiceState.PROCESSING, accepted.getState());
        assertTrue(accepted.isAsyncAccepted());
        assertTrue(accepted.getFiles().isEmpty());

        InvoiceResult polled = gateway.query(new InvoiceQueryRequest()
                .setAppId("demo").setChannelCode("mock")
                .setInvoice(accepted.getInvoice()));
        assertSame(InvoiceState.SUCCEEDED, polled.getState());
        assertTrue(polled.hasLegalXml());
    }

    /** 渠道失败：归一为 FAILED，不取得文件。 */
    @Test
    void failedIssueReturnsFailedState() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams().setIssueOutcome("FAIL"));
        InvoiceResult result = gateway.issue(request("B3"));
        assertSame(InvoiceState.FAILED, result.getState());
        assertEquals("MOCK_FAIL", result.getErrorCode());
        assertTrue(result.getFiles().isEmpty());
    }

    /** 快捷红冲：免确认时一次调用返回已开具红票。 */
    @Test
    void quickRedAutoIssue() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams()
                .setIssueOutcome("SUCCESS").setRedConfirmOutcome("ACCEPTED"));
        InvoiceResult issued = gateway.issue(request("B5"));
        assertSame(InvoiceState.SUCCEEDED, issued.getState());

        QuickRedResult quick = gateway.quickRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("R5")
                .setOriginalInvoice(issued.getInvoice()).setReason("测试快捷红冲"));
        assertNotNull(quick);
        assertNotNull(quick.getOutcome());
    }

    /** 确认单拒绝场景（渠道可选能力）。 */
    @Test
    void confirmRedAction() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());
        InvoiceResult issued = gateway.issue(request("B6"));
        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("R6")
                .setOriginalInvoice(issued.getInvoice()).setReason("拒绝测试"));
        // mock 渠道未实现主动确认能力 → SDK 明确抛出，由使用方改走查询状态流程
        GatewayException ex = assertThrows(GatewayException.class, () ->
                gateway.confirmRed(new RedConfirmRequest()
                        .setAppId("demo").setRedApply(apply).setAccept(false).setReason("不同意")));
        assertSame(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED, ex.getErrorCode());
    }

    /** 回调：只验签归一为事件，不查单、不推进状态。 */
    @Test
    void callbackOnlyParsesToEvent() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());
        Map<String, String> query = new HashMap<String, String>();
        query.put(InvoiceAccountKey.ROUTING_PARAM, InvoiceAccountKey.of(APP_KEY, TAX));
        String body = "{\"bizOrderNo\":\"FROM-CALLBACK\",\"state\":\"SUCCEEDED\","
                + "\"invoiceNo\":\"FP-CALLBACK\",\"xml\":\"<EInvoice>callback</EInvoice>\"}";
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode("mock").setQueryParams(query).setBody(body);

        InvoiceCallbackEvent event = gateway.parseCallback(raw);
        assertNotNull(event);
        assertEquals("FROM-CALLBACK", event.getBizOrderNo());
        assertSame(InvoiceState.SUCCEEDED, event.getState());
        assertEquals("FP-CALLBACK", event.getInvoiceNo());
    }

    /** 验签失败直接上抛，使用方据此应答渠道重推。 */
    @Test
    void callbackBadSignThrows() {
        InvoiceGateway gateway = gateway(new MockInvoiceParams());
        Map<String, String> query = new HashMap<String, String>();
        query.put(InvoiceAccountKey.ROUTING_PARAM, InvoiceAccountKey.of(APP_KEY, TAX));
        query.put("sign", "BAD-SIGN");
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode("mock").setQueryParams(query).setBody("{}");
        GatewayException ex = assertThrows(GatewayException.class, () -> gateway.parseCallback(raw));
        assertSame(InvoiceErrorCode.SIGN_INVALID, ex.getErrorCode());
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
        return InvoiceGateway.builder().configProvider(provider)
                .channel(new MockInvoiceChannelProvider()).build();
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
}
