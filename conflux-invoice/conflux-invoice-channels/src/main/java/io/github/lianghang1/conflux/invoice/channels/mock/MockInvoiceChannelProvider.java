package io.github.lianghang1.conflux.invoice.channels.mock;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.channel.AuthSessionHandler;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceCallbackHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.AuthSessionState;
import io.github.lianghang1.conflux.invoice.model.AuthType;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * mock 发票渠道：完整覆盖蓝字开票、等待授权、异步查单、XML 法定原件归档、
 * 红字确认单两段红冲、回调验签/解析，全部在进程内完成、零网络零凭据。
 *
 * <p>用于端到端演示与回归测试，也作为「如何实现 InvoiceChannelProvider」的参考样板。
 * Provider 本身无状态、线程安全；模拟的渠道台账仅用于演示，不持久化。</p>
 *
 * @author lianghang
 */
@Slf4j
public class MockInvoiceChannelProvider implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceFileFetcher, InvoiceRedHandler,
        AuthSessionHandler, InvoiceCallbackHandler {

    public static final String CODE = "mock";

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
    public ChannelConfig parseParams(Map<String, String> properties) {
        Map<String, String> props = properties == null
                ? Collections.<String, String>emptyMap() : properties;
        MockInvoiceParams params = new MockInvoiceParams();
        if (StringUtils.isNotBlank(props.get("appKey"))) {
            params.setChannelAppId(props.get("appKey"));
        }
        if (StringUtils.isNotBlank(props.get("channelAppId"))) {
            params.setChannelAppId(props.get("channelAppId"));
        }
        if (StringUtils.isNotBlank(props.get("sellerTaxNo"))) {
            params.setSellerTaxNo(props.get("sellerTaxNo"));
        }
        if (StringUtils.isNotBlank(props.get("appSecret"))) {
            params.setAppSecret(props.get("appSecret"));
        }
        if (StringUtils.isNotBlank(props.get("issueOutcome"))) {
            params.setIssueOutcome(props.get("issueOutcome"));
        }
        if (StringUtils.isNotBlank(props.get("redConfirmOutcome"))) {
            params.setRedConfirmOutcome(props.get("redConfirmOutcome"));
        }
        return params;
    }

    @Override
    public InvoiceIssuer issuer() {
        return this;
    }

    @Override
    public InvoiceQuerier querier() {
        return this;
    }

    @Override
    public InvoiceFileFetcher fileFetcher() {
        return this;
    }

    @Override
    public InvoiceRedHandler redHandler() {
        return this;
    }

    @Override
    public AuthSessionHandler authHandler() {
        return this;
    }

    @Override
    public InvoiceCallbackHandler callbackHandler() {
        return this;
    }

    // ---- 蓝字开票 ----

    @Override
    public InvoiceResult issue(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        MockInvoiceParams params = params(ctx);
        String outcome = params.getIssueOutcome();
        String channelOrderNo = channelOrderNo(invoice);

        if ("FAIL".equalsIgnoreCase(outcome)) {
            return InvoiceResult.failed("MOCK_FAIL", "mock 渠道按要求返回开票失败");
        }
        if ("ASYNC".equalsIgnoreCase(outcome)) {
            return InvoiceResult.accepted(channelOrderNo, true);
        }
        if ("AUTH".equalsIgnoreCase(outcome)) {
            AuthSession session = ctx.getAuthSession();
            if (session == null || session.getState() != AuthSessionState.VERIFIED) {
                return InvoiceResult.waitingAuth(smsChallenge(invoice));
            }
        }
        return success(channelOrderNo, invoice);
    }

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        // 模拟异步开票已完成：查单直接给出票号与法定原件
        return success(channelOrderNo(invoice), invoice);
    }

    @Override
    public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
        List<InvoiceFile> all = mockFiles(ctx.requireInvoice());
        if (types == null || types.isEmpty()) {
            return all;
        }
        List<InvoiceFile> filtered = new ArrayList<InvoiceFile>();
        for (InvoiceFile file : all) {
            if (types.contains(file.getType())) {
                filtered.add(file);
            }
        }
        return filtered;
    }

    // ---- 两段红冲 ----

    @Override
    public RedApplyResult apply(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        String bizRedNo = apply == null ? "unknown" : apply.getBizRedNo();
        return RedApplyResult.waiting("MOCK-RED-" + bizRedNo, LocalDateTime.now().plusHours(72L))
                .setRedConfirmNo("MOCK-CONFIRM-" + bizRedNo);
    }

    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        String outcome = params(ctx).getRedConfirmOutcome();
        RedApply apply = ctx.getRedApply();
        String confirmNo = apply == null || apply.getRedConfirmNo() == null
                ? "MOCK-CONFIRM" : apply.getRedConfirmNo();
        if ("REJECTED".equalsIgnoreCase(outcome)) {
            return new RedApplyResult().setState(RedApplyState.REJECTED)
                    .setErrorCode("MOCK_REJECTED").setErrorMsg("购方拒绝红字确认单");
        }
        if ("WAITING_CONFIRM".equalsIgnoreCase(outcome)) {
            return RedApplyResult.waiting(confirmNo, LocalDateTime.now().plusHours(72L));
        }
        return RedApplyResult.confirmed(confirmNo);
    }

    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        return success(channelOrderNo(ctx.requireInvoice()), ctx.requireInvoice());
    }

    @Override
    public QuickRedOutcome quickRed(InvoiceContext ctx) {
        RedApplyResult outcome = queryRed(ctx);
        if (outcome != null && outcome.getState() == RedApplyState.CONFIRMED) {
            return QuickRedOutcome.issued(issueRed(ctx), outcome);
        }
        return QuickRedOutcome.waiting(outcome == null
                ? RedApplyResult.waiting("MOCK-RED", LocalDateTime.now().plusHours(72L))
                : outcome);
    }

    // ---- 授权会话 ----

    @Override
    public AuthResult start(InvoiceContext ctx) {
        return AuthResult.pending(smsChallenge(ctx.requireInvoice()));
    }

    @Override
    public AuthResult submit(InvoiceContext ctx, AuthSubmission submission) {
        boolean hasCredential = StringUtils.isNotBlank(submission.getCaptcha())
                || StringUtils.isNotBlank(submission.getToken())
                || (submission.getExtra() != null && !submission.getExtra().isEmpty());
        if (!hasCredential) {
            return AuthResult.rejected("MOCK_AUTH_REJECTED", "验证码/授权 token 不能为空");
        }
        return AuthResult.verified(InvoiceState.SUBMITTING);
    }

    // ---- 回调 ----

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        String body = raw.getBody() == null ? "" : raw.getBody();
        MockInvoiceParams params = config instanceof MockInvoiceParams
                ? (MockInvoiceParams) config : new MockInvoiceParams();
        String sign = raw.getQueryParams() == null ? null : raw.getQueryParams().get("sign");
        if (StringUtils.isNotBlank(sign)) {
            String expected = SignUtil.hmacSha256Hex(body, params.getAppSecret());
            if (!expected.equalsIgnoreCase(sign)) {
                throw new GatewayException(InvoiceErrorCode.SIGN_INVALID, "mock 回调验签失败");
            }
        }
        JsonNode root = JsonUtils.readTree(body);
        if (root == null) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "mock 回调报文不是合法 JSON");
        }
        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setInvoiceId(text(root, "invoiceId"))
                .setRedApplyId(text(root, "redApplyId"))
                .setChannelOrderNo(text(root, "channelOrderNo", "reqId"))
                .setBizOrderNo(text(root, "bizOrderNo"))
                .setInvoiceNo(text(root, "invoiceNo"))
                .setInvoiceCode(text(root, "invoiceCode"))
                .setErrorCode(text(root, "errorCode"))
                .setErrorMsg(text(root, "errorMsg"))
                .setRawPayload(body);
        String state = text(root, "state", "status");
        if (StringUtils.isNotBlank(state)) {
            event.setState(InvoiceState.valueOf(state.trim().toUpperCase()));
        }
        String redState = text(root, "redState", "redStatus");
        if (StringUtils.isNotBlank(redState)) {
            event.setRedState(RedApplyState.valueOf(redState.trim().toUpperCase()));
        }
        String xml = text(root, "xml");
        if (StringUtils.isNotBlank(xml)) {
            event.setFiles(Collections.singletonList(
                    new InvoiceFile().setType(InvoiceFileType.XML).setContent(xml)));
        }
        return event;
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", success ? "SUCCESS" : "FAIL");
        body.put("message", message);
        return success ? InvoiceCallbackReply.ok(JsonUtils.toJson(body))
                : InvoiceCallbackReply.fail(message);
    }

    // ---- 内部工具 ----

    private MockInvoiceParams params(InvoiceContext ctx) {
        ChannelConfig config = ctx.getConfig();
        if (config != null && config instanceof MockInvoiceParams) {
            return (MockInvoiceParams) config;
        }
        return new MockInvoiceParams();
    }

    private AuthChallenge smsChallenge(Invoice invoice) {
        return new AuthChallenge()
                .setAuthType(AuthType.SMS)
                .setChallengeText("mock 短信验证码已发送，任意非空验证码即可通过")
                .setExpireTime(LocalDateTime.now().plusMinutes(30L));
    }

    private InvoiceResult success(String channelOrderNo, Invoice invoice) {
        return InvoiceResult.succeeded(channelOrderNo,
                "MOCK-FPHM-" + invoice.getInvoiceId(),
                "MOCK-FPDM", LocalDate.now(), mockFiles(invoice));
    }

    private String channelOrderNo(Invoice invoice) {
        return "MOCK-REQ-" + invoice.getInvoiceId();
    }

    /** 生成一份内联的 XML 法定原件 + OFD/PDF 下载地址。 */
    private List<InvoiceFile> mockFiles(Invoice invoice) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<EInvoice xmlns=\"urn:conflux:mock\">\n"
                + "  <InvoiceId>" + invoice.getInvoiceId() + "</InvoiceId>\n"
                + "  <BizOrderNo>" + invoice.getBizOrderNo() + "</BizOrderNo>\n"
                + "  <SellerTaxNo>" + invoice.getSellerTaxNo() + "</SellerTaxNo>\n"
                + "  <BuyerTitle>" + invoice.getBuyerTitle() + "</BuyerTitle>\n"
                + "  <Amount>" + invoice.getAmount() + "</Amount>\n"
                + "</EInvoice>";
        return Arrays.asList(
                new InvoiceFile().setType(InvoiceFileType.XML).setContent(xml),
                new InvoiceFile().setType(InvoiceFileType.OFD)
                        .setUrl("mock://ofd/" + invoice.getInvoiceId()),
                new InvoiceFile().setType(InvoiceFileType.PDF)
                        .setUrl("mock://pdf/" + invoice.getInvoiceId()));
    }

    private static String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && text.length() > 0) {
                    return text;
                }
            }
        }
        return null;
    }
}
