package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyQueryRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.AuthSessionState;
import io.github.lianghang1.conflux.invoice.model.AuthType;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import io.github.lianghang1.conflux.invoice.support.InvoiceFileMaterializer;
import io.github.lianghang1.conflux.invoice.util.InvoiceIds;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票编排器：开票 / 授权 / 查单 / 红冲 / 交付 / 修复 / 回调的渠道编排。
 *
 * <p>SDK 无状态——不落库、不推进状态机、不生成台账单号、不暂存会话。每次调用
 * 只做「组装渠道载体 → 调渠道 → 归一结果返回」。业务推进、判重、金额校验、
 * 业务联动全部由使用方完成；票据载体随结果返回、随后续请求回传（carrier round-trip）。</p>
 *
 * @author lianghang
 */
@Slf4j
class InvoiceOrchestrator {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private final InvoiceRuntime rt;
    private final ChannelInstanceRouter router;
    private final InvoiceFileMaterializer fileMaterializer;

    InvoiceOrchestrator(InvoiceRuntime rt, InvoiceFileOptions fileOptions) {
        this.rt = rt;
        this.router = new ChannelInstanceRouter(rt);
        // 文件固化是纯工具能力：下载/解码后写回载体，不落任何台账
        this.fileMaterializer = InvoiceFileMaterializer.detached(
                fileOptions == null ? new InvoiceFileOptions() : fileOptions);
    }

    // ==================== 蓝字开票 ====================

    /** 蓝字发票开具：组装载体 → 调渠道 → 归一返回。 */
    InvoiceResult issue(InvoiceRequest req) {
        validateIssueRequest(req);
        Invoice invoice = buildInvoice(req);
        ChannelInstance instance = router.route(req.getAppId(), req.getSellerTaxNo(),
                req.getInvoiceType(), req.getChannelCode());
        ChannelConfig config = resolveConfig(instance, req.getAppId(), req.getSellerTaxNo());
        rt.registry.assertTypeSupported(instance.getChannelCode(), req.getInvoiceType());
        invoice.setChannelCode(instance.getChannelCode())
                .setChannelInstanceId(instance.getInstanceId())
                .setAccountKey(instance.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, instance, invoice).setRequest(req);
        InvoiceResult outcome = rt.registry.issuer(invoice.getChannelCode()).issue(ctx);
        return applyOutcome(invoice, outcome);
    }

    /** 按请求组装发票载体（不落库；invoiceId 作为渠道交互标识）。 */
    private Invoice buildInvoice(InvoiceRequest req) {
        return new Invoice()
                .setInvoiceId(req.getBizOrderNo())
                .setAppId(req.getAppId())
                .setBizOrderNo(req.getBizOrderNo())
                .setSellerTaxNo(req.getSellerTaxNo())
                .setSellerName(req.getSellerName())
                .setBuyerTaxNo(req.getBuyerTaxNo())
                .setBuyerTitle(req.getBuyerTitle())
                .setBuyerAddress(req.getBuyerAddress())
                .setBuyerBank(req.getBuyerBank())
                .setBuyerPhone(req.getBuyerPhone())
                .setBuyerMobile(req.getBuyerMobile())
                .setBuyerEmail(req.getBuyerEmail())
                .setInvoiceType(req.getInvoiceType())
                .setItems(req.getItems())
                .setAmount(req.getAmount())
                .setTaxAmount(req.getTaxAmount())
                .setRemark(req.getRemark())
                .setDrawer(req.getDrawer())
                .setTspz(req.getTspz())
                .setZsfs(req.getZsfs())
                .setJzfwTdys(req.getJzfwTdys())
                .setBdcTdysList(req.getBdcTdysList())
                .setAsyncAllowed(req.isAsyncAllowed())
                .setSandbox(req.isSandbox())
                .setState(InvoiceState.CREATED)
                .setNotifyUrl(req.getNotifyUrl())
                .setAttrs(req.getAttrs());
    }

    // ==================== 查单 ====================

    /** 渠道查单：返回归一快照，状态推进由使用方完成。 */
    InvoiceResult query(InvoiceQueryRequest req) {
        req.check();
        Invoice invoice = req.getInvoice() != null ? req.getInvoice() : locate(req);
        ChannelConfig config = configOf(req.getAppId(), req.getChannelCode(), invoice);
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        InvoiceResult outcome = rt.registry.querier(req.getChannelCode()).query(ctx);
        return applyOutcome(invoice, outcome);
    }

    /** 无载体时按定位要素组装最小载体。 */
    private Invoice locate(InvoiceQueryRequest req) {
        return new Invoice()
                .setInvoiceId(req.getBizOrderNo())
                .setAppId(req.getAppId())
                .setBizOrderNo(req.getBizOrderNo())
                .setChannelCode(req.getChannelCode())
                .setChannelOrderNo(req.getChannelOrderNo())
                .setInvoiceNo(req.getInvoiceNo())
                .setInvoiceCode(req.getInvoiceCode());
    }

    // ==================== 授权两段式 ====================

    /** 提交授权凭证：SDK 不暂存会话，上下文随载体回传，授权通过后重发开票。 */
    InvoiceResult submitAuth(AuthSubmission submission) {
        submission.check();
        Invoice invoice = submission.getInvoice();
        AuthSession session = submission.getSession();
        if (session.getState() == AuthSessionState.EXPIRED) {
            throw new GatewayException(InvoiceErrorCode.AUTH_SESSION_EXPIRED, "授权会话已过期，请重新发起授权");
        }
        ChannelConfig config = configOf(submission.getAppId(), invoice.getChannelCode(), invoice);
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice).setAuthSession(session);
        AuthResult result = rt.registry.authHandler(invoice.getChannelCode()).submit(ctx, submission);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道授权未返回结果");
        }
        if (result.isVerified()) {
            session.setState(AuthSessionState.VERIFIED);
            // 授权通过：用原票载体重新发起开票
            InvoiceContext reissueCtx = InvoiceContext.of(config, null, invoice).setAuthSession(session);
            InvoiceResult outcome = rt.registry.issuer(invoice.getChannelCode()).issue(reissueCtx);
            InvoiceResult verified = applyOutcome(invoice, outcome);
            verified.setAuthSession(session);
            return verified;
        }
        if (result.isRetryable() && result.getChallenge() != null) {
            // 仍需授权：把新挑战写回会话载体返回，使用方继续持有上下文
            applyChallengeToSession(session, result.getChallenge());
            return InvoiceResult.fromInvoice(invoice, null, authChallengeOf(session)).setAuthSession(session);
        }
        session.setState(AuthSessionState.FAILED);
        InvoiceResult failed = InvoiceResult.failed(result.getErrorCode(), result.getErrorMsg());
        return failed.setInvoice(invoice).setAuthSession(session);
    }

    // ==================== 两段红冲 ====================

    /** 申请红字确认单（第一段）。 */
    RedApply applyRed(RedApplyRequest req) {
        req.check();
        Invoice original = req.getOriginalInvoice();
        String channel = StringUtils.isNotBlank(req.getChannelCode())
                ? req.getChannelCode() : original.getChannelCode();
        ChannelConfig config = configOf(req.getAppId(), channel, original);
        RedApply apply = buildRedApply(req, original, channel);
        InvoiceContext ctx = InvoiceContext.of(config, null, original).setRedApply(apply);
        RedApplyResult outcome = rt.registry.redHandler(channel).apply(ctx);
        return applyRedOutcome(apply, outcome);
    }

    /** 快捷红冲。 */
    QuickRedResult quickRed(RedApplyRequest req) {
        req.check();
        Invoice original = req.getOriginalInvoice();
        String channel = StringUtils.isNotBlank(req.getChannelCode())
                ? req.getChannelCode() : original.getChannelCode();
        ChannelConfig config = configOf(req.getAppId(), channel, original);
        RedApply apply = buildRedApply(req, original, channel);
        InvoiceContext ctx = InvoiceContext.of(config, null, original).setRedApply(apply);
        QuickRedOutcome outcome = rt.registry.redHandler(channel).quickRed(ctx);
        if (outcome == null || (outcome.getRedInvoice() == null && outcome.getRedApply() == null)) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道快捷红冲未返回结果");
        }
        if (outcome.isIssued()) {
            if (outcome.getRedApply() != null) {
                applyRedOutcome(apply, outcome.getRedApply());
            } else {
                apply.setState(RedApplyState.CONFIRMED);
            }
            Invoice red = createRedInvoice(apply, original, config, req.getNotifyUrl());
            // 渠道 quickRed 已通过快速接口（如百望 fastRed）一次调用开具红票，
            // 直接归一渠道已返回的结果即可，禁止二次调用 issueRed（否则重复开票）。
            InvoiceResult redInvoice = applyOutcome(red, outcome.getRedInvoice());
            return QuickRedResult.accepted(apply, redInvoice);
        }
        applyRedOutcome(apply, outcome.getRedApply());
        return QuickRedResult.waitingConfirm(apply);
    }

    /** 查询红字确认单状态：确认单载体由使用方回传。 */
    RedApply queryRedApply(RedApplyQueryRequest req) {
        req.check();
        RedApply apply = req.getRedApply();
        ChannelConfig config = configOfRed(req.getAppId(), apply);
        InvoiceContext ctx = InvoiceContext.of(config, null, null).setRedApply(apply);
        RedApplyResult outcome = rt.registry.redHandler(apply.getChannelCode()).queryRed(ctx);
        return applyRedOutcome(apply, outcome);
    }

    /** 主动确认 / 拒绝红字确认单。 */
    RedApply confirmRed(RedConfirmRequest req) {
        req.check();
        RedApply apply = req.getRedApply();
        ChannelConfig config = configOfRed(req.getAppId(), apply);
        InvoiceContext ctx = InvoiceContext.of(config, null, null).setRedApply(apply);
        RedApplyResult outcome = rt.registry.redHandler(apply.getChannelCode()).confirm(ctx, req);
        if (outcome == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "渠道[{}]不支持主动确认/拒绝红字确认单", apply.getChannelCode());
        }
        return applyRedOutcome(apply, outcome);
    }

    /** 确认后开具红字发票（第二段）。 */
    InvoiceResult issueRed(RedIssueRequest req) {
        req.check();
        RedApply apply = req.getRedApply();
        Invoice original = req.getOriginalInvoice();
        String channel = StringUtils.isNotBlank(req.getChannelCode())
                ? req.getChannelCode() : apply.getChannelCode();
        ChannelConfig config = configOf(req.getAppId(), channel, original);
        Invoice red = createRedInvoice(apply, original, config, req.getNotifyUrl());
        InvoiceContext ctx = InvoiceContext.of(config, null, red).setRedApply(apply);
        InvoiceResult outcome = rt.registry.redHandler(channel).issueRed(ctx);
        return applyOutcome(red, outcome);
    }

    /** 由请求与原票载体组装红字确认单（不落库）。 */
    private RedApply buildRedApply(RedApplyRequest req, Invoice original, String channel) {
        return new RedApply()
                .setRedApplyId(req.getBizRedNo())
                .setAppId(req.getAppId())
                .setBizRedNo(req.getBizRedNo())
                .setOriginalInvoiceId(original.getInvoiceId())
                .setOriginalInvoiceNo(original.getInvoiceNo())
                .setOriginalInvoiceCode(original.getInvoiceCode())
                .setReason(req.getReason())
                .setChyydm(req.getChyydm())
                .setAmount(req.getAmount() != null ? req.getAmount() : original.getAmount())
                .setChannelCode(channel)
                .setChannelInstanceId(original.getChannelInstanceId())
                .setAccountKey(original.getAccountKey())
                .setChannelOrderNo(original.getChannelOrderNo())
                .setState(RedApplyState.APPLYING)
                .setNotifyUrl(req.getNotifyUrl());
    }

    /** 建立红字发票载体。 */
    private Invoice createRedInvoice(RedApply apply, Invoice original, ChannelConfig config,
                                     String notifyUrl) {
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("RED_INVOICE", "true");
        return new Invoice()
                .setInvoiceId(apply.getBizRedNo())
                .setAppId(apply.getAppId())
                .setBizOrderNo(apply.getBizRedNo() + "-RED")
                .setChannelCode(apply.getChannelCode())
                .setChannelInstanceId(apply.getChannelInstanceId())
                .setAccountKey(apply.getAccountKey())
                .setSellerTaxNo(original.getSellerTaxNo())
                .setSellerName(original.getSellerName())
                .setBuyerTaxNo(original.getBuyerTaxNo())
                .setBuyerTitle(original.getBuyerTitle())
                .setInvoiceType(original.getInvoiceType())
                .setItems(original.getItems())
                .setAmount(apply.getAmount())
                .setTaxAmount(original.getTaxAmount())
                .setOriginalInvoiceId(apply.getOriginalInvoiceId())
                .setRedApplyId(apply.getRedApplyId())
                .setChannelOrderNo(apply.getChannelOrderNo())
                .setNotifyUrl(notifyUrl)
                .setState(InvoiceState.CREATED)
                .setAttrs(attrs);
    }

    // ==================== 辅助能力：交付 / 修复 ====================

    /** 重新交付发票（不改状态）。 */
    DeliveryResult resend(DeliveryRequest req) {
        req.check();
        Invoice invoice = req.getInvoice();
        ChannelConfig config = configOf(req.getAppId(), invoice.getChannelCode(), invoice);
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        DeliveryResult result = rt.registry.deliverer(invoice.getChannelCode()).resend(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道交付未返回结果");
        }
        return result;
    }

    /** 提交发票修复申请（不改状态）。 */
    RepairResult repair(RepairRequest req) {
        String channel = resolveRepairChannel(req);
        ChannelConfig config = configForRepair(req, channel);
        InvoiceContext ctx = InvoiceContext.of(config, null, null);
        RepairResult result = rt.registry.repairer(channel).apply(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道修复未返回结果");
        }
        return result;
    }

    /** 查询发票修复结果。 */
    RepairResult queryRepair(RepairRequest req) {
        String channel = resolveRepairChannel(req);
        ChannelConfig config = configForRepair(req, channel);
        InvoiceContext ctx = InvoiceContext.of(config, null, null);
        RepairResult result = rt.registry.repairer(channel).query(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道修复结果查询未返回结果");
        }
        return result;
    }

    private String resolveRepairChannel(RepairRequest req) {
        if (StringUtils.isNotBlank(req.getChannelCode())) {
            return req.getChannelCode();
        }
        throw new GatewayException(InvoiceErrorCode.REPAIR_PARAM_REQUIRED, "修复需指定 channelCode");
    }

    private ChannelConfig configForRepair(RepairRequest req, String channel) {
        if (StringUtils.isBlank(req.getAppId())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "修复需提供 appId 定位渠道配置");
        }
        return rt.configProvider.getConfig(req.getAppId(), channel, req.getSellerTaxNo());
    }

    // ==================== 渠道回调 ====================

    /** 验签并解析渠道回调为归一事件；不查单、不校验金额、不推进状态。 */
    InvoiceCallbackEvent parseCallback(InvoiceCallbackRequest raw) {
        if (raw == null || StringUtils.isBlank(raw.getChannelCode())) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "回调报文缺少 channelCode");
        }
        String code = raw.getChannelCode();
        String accountKey = InvoiceAccountKey.resolve(raw, null);
        if (StringUtils.isBlank(accountKey)) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "回调地址缺少 {} 参数，无法反查账户配置", InvoiceAccountKey.ROUTING_PARAM);
        }
        ChannelConfig config = rt.configProvider.getByAccountKey(code, accountKey);
        InvoiceCallbackEvent event = rt.registry.callbackHandler(code).parse(raw, config);
        if (event == null) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "回调解析结果为空");
        }
        return event;
    }

    // ==================== 文件工具 ====================

    /** 对结果中的版式文件就地固化（下载/解码），不落任何台账。 */
    List<InvoiceFile> materializeFiles(List<InvoiceFile> files, String invoiceId, boolean strict) {
        List<InvoiceFile> normalized = normalizeFiles(files, invoiceId);
        fileMaterializer.materialize(normalized, strict);
        return normalized;
    }

    /** 取文件字节：内联解码；只有 url 时下载，失效时抛 FILE_URL_EXPIRED。 */
    byte[] fileBytes(InvoiceFile file) {
        if (file == null) {
            throw new GatewayException(InvoiceErrorCode.FILE_NOT_FOUND, "发票版式文件不存在");
        }
        return fileMaterializer.materializeBytes(file);
    }

    private List<InvoiceFile> normalizeFiles(List<InvoiceFile> files, String invoiceId) {
        List<InvoiceFile> normalized = new ArrayList<InvoiceFile>();
        if (files == null) {
            return normalized;
        }
        for (InvoiceFile file : files) {
            if (file == null || file.getType() == null || !file.hasPayload()) {
                continue;
            }
            file.setInvoiceId(invoiceId);
            if (file.getFileId() == null) {
                file.setFileId(InvoiceIds.file());
            }
            normalized.add(file);
        }
        return normalized;
    }

    // ==================== 渠道结果归一 ====================

    /** 把渠道四态结果归一到载体并返回对外结果；文件就地固化（不落库）。 */
    private InvoiceResult applyOutcome(Invoice invoice, InvoiceResult outcome) {
        if (outcome == null || outcome.getState() == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "渠道未返回明确单据状态: bizOrderNo={}", invoice.getBizOrderNo());
        }
        InvoiceState target = outcome.getState();
        if (outcome.getChannelOrderNo() != null) {
            invoice.setChannelOrderNo(outcome.getChannelOrderNo());
        }
        invoice.setState(target);
        switch (target) {
            case WAITING_AUTH: {
                AuthChallenge challenge = outcome.getAuthChallenge();
                AuthSession session = buildAuthSession(invoice, challenge);
                return InvoiceResult.fromInvoice(invoice, null, authChallengeOf(session))
                        .setAuthSession(session);
            }
            case SUCCEEDED: {
                assertAmountMatch(invoice, outcome);
                List<InvoiceFile> files = materializeFiles(outcome.getFiles(), invoice.getInvoiceId(), true);
                assertLegalXml(files);
                if (outcome.getInvoiceNo() != null) {
                    invoice.setInvoiceNo(outcome.getInvoiceNo());
                }
                if (outcome.getInvoiceCode() != null) {
                    invoice.setInvoiceCode(outcome.getInvoiceCode());
                }
                if (outcome.getInvoiceDate() != null) {
                    invoice.setInvoiceDate(outcome.getInvoiceDate());
                }
                if (outcome.getInvoiceTime() != null) {
                    invoice.setInvoiceTime(outcome.getInvoiceTime());
                }
                return InvoiceResult.fromInvoice(invoice, files, null);
            }
            case FAILED:
                invoice.setErrorCode(outcome.getErrorCode()).setErrorMsg(outcome.getErrorMsg());
                return InvoiceResult.fromInvoice(invoice, null, null);
            case PROCESSING:
            default:
                return InvoiceResult.fromInvoice(invoice, null, null);
        }
    }

    /** 把红字申请结果归一到确认单载体。 */
    private RedApply applyRedOutcome(RedApply apply, RedApplyResult outcome) {
        if (outcome == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道红字申请未返回结果");
        }
        if (outcome.getChannelOrderNo() != null) {
            apply.setChannelOrderNo(outcome.getChannelOrderNo());
        }
        if (outcome.getRedConfirmNo() != null) {
            apply.setRedConfirmNo(outcome.getRedConfirmNo());
        }
        if (outcome.getConfirmDeadline() != null) {
            apply.setConfirmDeadline(outcome.getConfirmDeadline());
        }
        if (outcome.getState() != null) {
            apply.setState(outcome.getState());
        }
        apply.setErrorCode(outcome.getErrorCode()).setErrorMsg(outcome.getErrorMsg());
        return apply;
    }

    /** 由渠道挑战构建授权会话载体（上下文随载体回传，SDK 不存）。 */
    private AuthSession buildAuthSession(Invoice invoice, AuthChallenge challenge) {
        AuthChallenge c = challenge == null ? new AuthChallenge() : challenge;
        return new AuthSession()
                .setSessionId(StringUtils.isNotBlank(c.getSessionId()) ? c.getSessionId() : InvoiceIds.authSession())
                .setInvoiceId(invoice.getInvoiceId())
                .setAppId(invoice.getAppId())
                .setChannelCode(invoice.getChannelCode())
                .setAccountKey(invoice.getAccountKey())
                .setAuthType(c.getAuthType() == null ? AuthType.SMS : c.getAuthType())
                .setState(AuthSessionState.PENDING)
                .setChallengeUrl(c.getChallengeUrl())
                .setChallengeText(c.getChallengeText())
                .setExpireTime(c.getExpireTime())
                .setPayload(c.getExtra());
    }

    private AuthChallenge authChallengeOf(AuthSession session) {
        return new AuthChallenge()
                .setSessionId(session.getSessionId())
                .setAuthType(session.getAuthType())
                .setChallengeUrl(session.getChallengeUrl())
                .setChallengeText(session.getChallengeText())
                .setExpireTime(session.getExpireTime())
                .setExtra(session.getPayload());
    }

    private void applyChallengeToSession(AuthSession session, AuthChallenge challenge) {
        session.setAuthType(challenge.getAuthType() == null ? session.getAuthType() : challenge.getAuthType())
                .setChallengeUrl(challenge.getChallengeUrl())
                .setChallengeText(challenge.getChallengeText())
                .setExpireTime(challenge.getExpireTime() == null ? session.getExpireTime() : challenge.getExpireTime())
                .setPayload(challenge.getExtra() == null ? session.getPayload() : challenge.getExtra())
                .setState(AuthSessionState.PENDING);
    }

    private void assertLegalXml(List<InvoiceFile> files) {
        for (InvoiceFile file : files) {
            if (file.getType() == InvoiceFileType.XML && file.hasPayload()) {
                return;
            }
        }
        throw new GatewayException(InvoiceErrorCode.XML_ORIGINAL_REQUIRED,
                "渠道回传开票成功但未取得数电票 XML 法定原件");
    }

    private void assertAmountMatch(Invoice invoice, InvoiceResult outcome) {
        if (outcome.getAmount() == null || invoice.getAmount() == null) {
            return;
        }
        if (outcome.getAmount().subtract(invoice.getAmount()).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            throw new GatewayException(InvoiceErrorCode.AMOUNT_MISMATCH,
                    "渠道回传金额[{}]与请求金额[{}]不一致", outcome.getAmount(), invoice.getAmount());
        }
    }

    /** 红冲确认单场景：优先用载体上的账户键反查配置。 */
    private ChannelConfig configOfRed(String appId, RedApply apply) {
        if (StringUtils.isBlank(apply.getChannelCode())) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_NOT_FOUND, "确认单缺少渠道编码");
        }
        if (StringUtils.isNotBlank(apply.getAccountKey())) {
            return rt.configProvider.getByAccountKey(apply.getChannelCode(), apply.getAccountKey());
        }
        return rt.configProvider.getConfig(appId, apply.getChannelCode(), null);
    }

    private ChannelConfig configOf(String appId, String channelCode, Invoice invoice) {
        if (StringUtils.isBlank(channelCode)) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_NOT_FOUND, "缺少渠道编码");
        }
        String accountKey = invoice == null ? null : invoice.getAccountKey();
        if (StringUtils.isNotBlank(accountKey)) {
            return rt.configProvider.getByAccountKey(channelCode, accountKey);
        }
        String sellerTaxNo = invoice == null ? null : invoice.getSellerTaxNo();
        return rt.configProvider.getConfig(appId, channelCode, sellerTaxNo);
    }

    private ChannelConfig resolveConfig(ChannelInstance instance, String appId, String sellerTaxNo) {
        return instance.getConfig() != null ? instance.getConfig()
                : rt.configProvider.getConfig(appId, instance.getChannelCode(), sellerTaxNo);
    }

    private void validateIssueRequest(InvoiceRequest req) {
        GatewayAssert.param(req != null, "开票请求不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getAppId()), "appId 不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getBizOrderNo()), "bizOrderNo 不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getSellerTaxNo()), "sellerTaxNo 不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getBuyerTitle()), "buyerTitle 不能为空");
        GatewayAssert.param(req.getInvoiceType() != null, "invoiceType 不能为空");
        GatewayAssert.param(req.getItems() != null && !req.getItems().isEmpty(), "明细行不能为空");
        GatewayAssert.param(req.getAmount() != null, "含税合计 amount 不能为空");
        BigDecimal sum = BigDecimal.ZERO;
        for (InvoiceItem item : req.getItems()) {
            if (item != null && item.getAmount() != null) {
                sum = sum.add(item.getAmount());
            }
        }
        GatewayAssert.param(req.getAmount().subtract(sum).abs().compareTo(AMOUNT_TOLERANCE) <= 0,
                "明细行金额合计与含税合计不一致: amount={}, itemsSum={}", req.getAmount(), sum);
    }
}
