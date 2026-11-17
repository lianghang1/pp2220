package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.SignUtil;
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
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.api.RedAvailableResult;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
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
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 发票编排引擎：开票 / 授权 / 查单 / 两段红冲 / 回调共用同一套状态机与持久化契约。
 *
 * <p><b>与支付编排器的三个关键差异</b>：</p>
 * <ol>
 *   <li>渠道结果不是「成功/失败」二元，而是四种：异步受理（PROCESSING）、
 *       需要人工授权（WAITING_AUTH）、同步成功（SUCCEEDED）、失败（FAILED）；</li>
 *   <li>开票成功前必须归档至少一份 {@link InvoiceFileType#XML} 法定原件，
 *       否则拒绝置为 SUCCEEDED；</li>
 *   <li>每次状态迁移都走 {@link #transition} 的 CAS 写入
 *       （{@code InvoiceStore.tryUpdate(invoice, expectedVersion)}），
 *       并发冲突时放弃本次迁移并抛 {@code STORE_CONCURRENT_MODIFICATION} 让补单/回调重试，
 *       避免支付域「回调线程与补单线程互相全量覆盖」的契约缺位。</li>
 * </ol>
 *
 * @author lianghang
 */
@Slf4j
public class InvoiceOrchestrator {

    /** 授权会话默认有效期（分钟）。 */
    private static final long AUTH_SESSION_MINUTES = 30L;
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final InvoiceRuntime rt;
    private final ChannelInstanceRouter router;
    private final InvoiceFileMaterializer fileMaterializer;

    public InvoiceOrchestrator(InvoiceRuntime rt) {
        this.rt = rt;
        this.router = new ChannelInstanceRouter(rt);
        // 固化结果回写 FileStore（幂等），调用方无需再关心 url 型文件
        this.fileMaterializer = new InvoiceFileMaterializer(rt.getFileOptions(),
                new InvoiceFileMaterializer.PersistCallback() {
                    @Override
                    public void persist(List<InvoiceFile> files) {
                        rt.fileStore.saveAll(files);
                    }
                });
    }

    // ==================== 蓝字开票 ====================

    /** 蓝字发票开具；相同 appId + bizOrderNo 且关键参数一致时幂等返回原单据。 */
    public InvoiceResult issue(InvoiceRequest req) {
        validateIssueRequest(req);

        Invoice existing = rt.invoiceStore.findByBizOrderNo(req.getAppId(), req.getBizOrderNo());
        if (existing != null) {
            assertIdempotent(existing, req);
            return buildResult(existing);
        }

        ChannelInstance instance = router.route(req.getAppId(), req.getSellerTaxNo(),
                req.getInvoiceType(), req.getChannelCode());
        ChannelConfig config = resolveConfig(instance, req.getAppId(), req.getSellerTaxNo());
        rt.registry.assertTypeSupported(instance.getChannelCode(), req.getInvoiceType());

        LocalDateTime now = rt.now();
        Invoice invoice = new Invoice()
                .setInvoiceId(InvoiceIds.invoice())
                .setAppId(req.getAppId())
                .setBizOrderNo(req.getBizOrderNo())
                .setChannelCode(instance.getChannelCode())
                .setChannelInstanceId(instance.getInstanceId())
                .setAccountKey(instance.getAccountKey())
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
                .setNotifyUrl(req.getNotifyUrl())
                .setSandbox(req.isSandbox())
                .setState(InvoiceState.CREATED)
                .setVersion(0L)
                .setCreatedTime(now)
                .setUpdatedTime(now)
                .setAttrs(req.getAttrs());
        rt.invoiceStore.save(invoice);
        rt.publish(InvoiceEvent.Type.CREATED, invoice, null, "发票单据建单完成");

        transition(invoice, InvoiceState.SUBMITTING, null);
        InvoiceContext ctx = InvoiceContext.of(config, instance, invoice).setRequest(req);
        InvoiceResult outcome = rt.registry.issuer(invoice.getChannelCode()).issue(ctx);
        applyOutcome(invoice, outcome, ctx);
        return buildResult(invoice);
    }

    // ==================== 查单 / 补单 ====================

    /** 查单：在途单据顺带向渠道查询并推进状态；终态直接返回台账。 */
    public InvoiceResult query(InvoiceQueryRequest req) {
        GatewayAssert.param(req != null, "查单请求不能为空");
        Invoice invoice = locate(req);
        if (invoice == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND, "发票单据不存在");
        }
        refreshFromChannel(invoice);
        return buildResult(invoice);
    }

    /** 强制刷新（补单扫描用）；找不到返回 null。 */
    public InvoiceResult refresh(String invoiceId) {
        Invoice invoice = rt.invoiceStore.findById(invoiceId);
        if (invoice == null) {
            return null;
        }
        refreshFromChannel(invoice);
        return buildResult(invoice);
    }

    private void refreshFromChannel(Invoice invoice) {
        if (!invoice.getState().isInFlight()) {
            return;
        }
        ChannelConfig config = configOf(invoice.getChannelCode(), invoice.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        InvoiceResult outcome = rt.registry.querier(invoice.getChannelCode()).query(ctx);
        applyOutcome(invoice, outcome, ctx);
    }

    private Invoice locate(InvoiceQueryRequest req) {
        if (StringUtils.isNotBlank(req.getInvoiceId())) {
            return rt.invoiceStore.findById(req.getInvoiceId());
        }
        if (StringUtils.isNotBlank(req.getBizOrderNo())) {
            return rt.invoiceStore.findByBizOrderNo(req.getAppId(), req.getBizOrderNo());
        }
        if (StringUtils.isNotBlank(req.getChannelOrderNo())) {
            return rt.invoiceStore.findByChannelOrderNo(req.getChannelOrderNo());
        }
        if (StringUtils.isNotBlank(req.getInvoiceNo())) {
            return rt.invoiceStore.findByInvoiceNo(req.getInvoiceNo(), req.getInvoiceCode());
        }
        GatewayAssert.param(false,
                "查单参数不足：需提供 invoiceId / bizOrderNo / channelOrderNo / invoiceNo");
        return null;
    }

    // ==================== 授权会话 ====================

    /** 提交授权凭证；通过后会重新发起开票，未通过且可重试则保持 WAITING_AUTH。 */
    public InvoiceResult submitAuth(AuthSubmission submission) {
        GatewayAssert.param(submission != null, "授权提交不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(submission.getSessionId()), "sessionId 不能为空");

        AuthSession session = rt.authSessionStore.findById(submission.getSessionId());
        if (session == null) {
            throw new GatewayException(InvoiceErrorCode.AUTH_SESSION_NOT_FOUND,
                    "授权会话不存在: {}", submission.getSessionId());
        }
        if (session.isExpired(rt.now())) {
            updateSessionState(session, AuthSessionState.EXPIRED);
            throw new GatewayException(InvoiceErrorCode.AUTH_SESSION_EXPIRED,
                    "授权会话已过期，请重新发起授权: {}", session.getSessionId());
        }
        if (session.getState() != AuthSessionState.PENDING) {
            throw new GatewayException(InvoiceErrorCode.AUTH_SESSION_STATE_ILLEGAL,
                    "授权会话当前状态不允许提交: {}", session.getState());
        }

        Invoice invoice = rt.invoiceStore.findById(session.getInvoiceId());
        if (invoice == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND,
                    "授权会话关联的单据不存在: {}", session.getInvoiceId());
        }
        ChannelConfig config = configOf(invoice.getChannelCode(), invoice.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice).setAuthSession(session);

        AuthResult result = rt.registry.authHandler(invoice.getChannelCode()).submit(ctx, submission);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道授权未返回结果");
        }
        updateSessionState(session, AuthSessionState.SUBMITTED);

        if (result.isVerified()) {
            updateSessionState(session, AuthSessionState.VERIFIED);
            rt.publish(InvoiceEvent.Type.AUTH_VERIFIED, invoice, null, "授权校验通过");
            if (invoice.getState() == InvoiceState.WAITING_AUTH) {
                transition(invoice, InvoiceState.SUBMITTING, null);
            }
            InvoiceResult outcome = rt.registry.issuer(invoice.getChannelCode()).issue(ctx);
            applyOutcome(invoice, outcome, ctx);
        } else if (result.isRetryable()) {
            applyChallengeToSession(session, result.getChallenge());
            log.debug("授权仍未完成，保持挂起: invoiceId={}, sessionId={}",
                    invoice.getInvoiceId(), session.getSessionId());
        } else {
            updateSessionState(session, AuthSessionState.FAILED);
            final AuthResult failed = result;
            transition(invoice, InvoiceState.FAILED, new Consumer<Invoice>() {
                @Override
                public void accept(Invoice inv) {
                    inv.setErrorCode(failed.getErrorCode()).setErrorMsg(failed.getErrorMsg());
                }
            });
        }
        return buildResult(invoice);
    }

    // ==================== 两段红冲 ====================

    /** 申请红字确认单（第一段）。 */
    public RedApply applyRed(RedApplyRequest req) {
        validateRedRequest(req);
        RedApply existing = rt.redApplyStore.findByBizRedNo(req.getAppId(), req.getBizRedNo());
        if (existing != null) {
            return existing;
        }
        Invoice original = requireRedOriginal(req);
        ChannelConfig config = configOf(original.getChannelCode(), original.getAccountKey());
        RedApply apply = buildRedApply(req, original);
        rt.redApplyStore.save(apply);

        InvoiceContext ctx = InvoiceContext.of(config, null, original).setRedApply(apply);
        RedApplyResult result = rt.registry.redHandler(original.getChannelCode()).apply(ctx);
        updateRedApply(apply, result);
        markOriginalRedApplying(original, apply.getRedApplyId());
        return apply;
    }

    /** 定位并校验红冲原票。 */
    private Invoice requireRedOriginal(RedApplyRequest req) {
        Invoice original = locateOriginal(req);
        if (original == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND, "红冲原票不存在");
        }
        if (original.getState() != InvoiceState.SUCCEEDED) {
            throw new GatewayException(InvoiceErrorCode.STATE_ILLEGAL,
                    "仅开票成功的蓝字发票可申请红冲，当前状态={}", original.getState());
        }
        ChannelConfig config = configOf(original.getChannelCode(), original.getAccountKey());
        assertRedAvailable(original, config, req);
        return original;
    }

    /** 由请求与原票构建红字确认单单据（未落库）。 */
    private RedApply buildRedApply(RedApplyRequest req, Invoice original) {
        LocalDateTime now = rt.now();
        return new RedApply()
                .setRedApplyId(InvoiceIds.redApply())
                .setAppId(req.getAppId())
                .setBizRedNo(req.getBizRedNo())
                .setOriginalInvoiceId(original.getInvoiceId())
                .setOriginalInvoiceNo(original.getInvoiceNo())
                .setOriginalInvoiceCode(original.getInvoiceCode())
                .setReason(req.getReason())
                .setChyydm(req.getChyydm())
                .setAmount(req.getAmount() != null ? req.getAmount() : original.getAmount())
                .setChannelCode(original.getChannelCode())
                .setChannelInstanceId(original.getChannelInstanceId())
                .setAccountKey(original.getAccountKey())
                .setState(RedApplyState.APPLYING)
                .setNotifyUrl(req.getNotifyUrl())
                .setVersion(0L)
                .setCreatedTime(now)
                .setUpdatedTime(now);
    }

    /** 标记原票进入红冲申请中。 */
    private void markOriginalRedApplying(Invoice original, final String redApplyId) {
        transition(original, InvoiceState.RED_APPLYING, new Consumer<Invoice>() {
            @Override
            public void accept(Invoice inv) {
                inv.setRedApplyId(redApplyId);
            }
        });
    }

    /**
     * 快捷红冲：一次调用覆盖红冲全流程。
     *
     * <p>渠道一次调用后返回红票受理结果或待确认状态；渠道免确认已开具红票时
     * 建红票单据并落 CONFIRMED，仍需对方确认时落 WAITING_CONFIRM，由使用方
     * 后续确认并开具。</p>
     */
    public QuickRedResult quickRed(RedApplyRequest req) {
        validateRedRequest(req);
        RedApply existing = rt.redApplyStore.findByBizRedNo(req.getAppId(), req.getBizRedNo());
        if (existing != null) {
            return quickRedResultOf(existing);
        }
        Invoice original = requireRedOriginal(req);
        ChannelConfig config = configOf(original.getChannelCode(), original.getAccountKey());
        RedApply apply = buildRedApply(req, original);
        rt.redApplyStore.save(apply);
        InvoiceContext applyCtx = InvoiceContext.of(config, null, original).setRedApply(apply);

        QuickRedOutcome outcome = rt.registry.redHandler(original.getChannelCode())
                .quickRed(applyCtx);
        if (outcome == null || (outcome.getRedInvoice() == null && outcome.getRedApply() == null)) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道快捷红冲未返回结果");
        }

        if (outcome.isIssued()) {
            RedApplyResult confirm = outcome.getRedApply() == null
                    ? RedApplyResult.confirmed(null) : outcome.getRedApply();
            updateRedApply(apply, confirm);
            markOriginalRedApplying(original, apply.getRedApplyId());
            Invoice red = createRedInvoice(apply, original, config, req.getNotifyUrl());
            InvoiceContext redCtx = InvoiceContext.of(config, null, red).setRedApply(apply);
            InvoiceResult redInvoice = settleRedIssue(red, outcome.getRedInvoice(), redCtx);
            return QuickRedResult.accepted(apply, redInvoice);
        }

        updateRedApply(apply, outcome.getRedApply());
        markOriginalRedApplying(original, apply.getRedApplyId());
        return QuickRedResult.waitingConfirm(apply);
    }

    /** 已存在确认单时按当前状态还原快捷红冲结果。 */
    private QuickRedResult quickRedResultOf(RedApply apply) {
        if (apply.getState().canIssueRed() || apply.getState() == RedApplyState.ISSUED) {
            Invoice red = StringUtils.isBlank(apply.getRedInvoiceId()) ? null
                    : rt.invoiceStore.findById(apply.getRedInvoiceId());
            if (red == null) {
                // 红票在途时确认单尚未回写 redInvoiceId（仅开票成功才写入），
                // 按红票业务单号反查已建单据；校验 redApplyId 避免误命中同名业务单
                Invoice candidate = rt.invoiceStore.findByBizOrderNo(
                        apply.getAppId(), apply.getBizRedNo() + "-RED");
                if (candidate != null && apply.getRedApplyId().equals(candidate.getRedApplyId())) {
                    red = candidate;
                }
            }
            return QuickRedResult.accepted(apply, red == null ? null : buildResult(red));
        }
        return QuickRedResult.waitingConfirm(apply);
    }

    /** 查询红字确认单确认状态（第二段之前的确认环节）。 */
    public RedApply queryRedApply(String redApplyId) {
        RedApply apply = rt.redApplyStore.findById(redApplyId);
        if (apply == null) {
            throw new GatewayException(InvoiceErrorCode.RED_APPLY_NOT_FOUND,
                    "红字确认单不存在: {}", redApplyId);
        }
        if (apply.getState().isTerminal() || apply.getState() != RedApplyState.WAITING_CONFIRM) {
            return apply;
        }
        if (apply.getConfirmDeadline() != null && !rt.now().isBefore(apply.getConfirmDeadline())) {
            updateRedApply(apply, new RedApplyResult().setState(RedApplyState.EXPIRED));
            return apply;
        }
        Invoice original = rt.invoiceStore.findById(apply.getOriginalInvoiceId());
        ChannelConfig config = configOf(apply.getChannelCode(), apply.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, original).setRedApply(apply);
        RedApplyResult result = rt.registry.redHandler(apply.getChannelCode()).queryRed(ctx);
        applyConfirmResult(apply, original, result);
        return apply;
    }

    /**
     * 主动确认或拒绝红字确认单（与 {@link #queryRedApply(String)} 的「查状态」分开）。
     *
     * <p>渠道未实现该可选能力时返回 {@code CHANNEL_CAPABILITY_NOT_SUPPORTED}，
     * 由使用方决定改走查询状态流程，不静默降级。</p>
     */
    public RedApply confirmRed(RedConfirmRequest req) {
        GatewayAssert.param(req != null, "红字确认请求不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getRedApplyId()), "redApplyId 不能为空");
        RedApply apply = rt.redApplyStore.findById(req.getRedApplyId());
        if (apply == null) {
            throw new GatewayException(InvoiceErrorCode.RED_APPLY_NOT_FOUND,
                    "红字确认单不存在: {}", req.getRedApplyId());
        }
        if (apply.getState().isTerminal() || apply.getState() != RedApplyState.WAITING_CONFIRM) {
            return apply;
        }
        if (apply.getConfirmDeadline() != null && !rt.now().isBefore(apply.getConfirmDeadline())) {
            updateRedApply(apply, new RedApplyResult().setState(RedApplyState.EXPIRED));
            return apply;
        }
        Invoice original = rt.invoiceStore.findById(apply.getOriginalInvoiceId());
        ChannelConfig config = configOf(apply.getChannelCode(), apply.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, original).setRedApply(apply);
        RedApplyResult result = rt.registry.redHandler(apply.getChannelCode()).confirm(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "渠道[{}]不支持主动确认/拒绝红字确认单，请改用查询确认状态流程",
                    apply.getChannelCode());
        }
        applyConfirmResult(apply, original, result);
        return apply;
    }

    /** 确认结果落库 + 原票状态推进（查询与主动确认共用）。 */
    private void applyConfirmResult(RedApply apply, Invoice original, RedApplyResult result) {
        updateRedApply(apply, result);
        if (apply.getState() == RedApplyState.CONFIRMED && original != null
                && original.getState() == InvoiceState.RED_APPLYING) {
            transition(original, InvoiceState.RED_APPLY_CONFIRMED, null);
        }
    }

    /**
     * 红冲前置校验：渠道实现了可冲销明细能力且明确返回「不可冲」时阻断。
     * 渠道不支持（返回 null）或异步受理（结果走回调）时一律放行。
     */
    private void assertRedAvailable(Invoice original, ChannelConfig config, RedApplyRequest req) {
        RedAvailableResult check = rt.registry.redHandler(original.getChannelCode())
                .checkRedAvailable(InvoiceContext.of(config, null, original));
        if (check == null || check.isAsyncAccepted() || check.isAvailable()) {
            return;
        }
        throw new GatewayException(InvoiceErrorCode.RED_NOT_AVAILABLE,
                "原票[{}]可冲销校验未通过，不能发起红冲: {}",
                req.getOriginalInvoiceNo(), check.getErrorMsg() == null
                        ? "可红冲余额不足或原票状态不允许" : check.getErrorMsg());
    }

    /** 确认后开具红字发票（第二段）。 */
    public InvoiceResult issueRed(RedIssueRequest req) {
        GatewayAssert.param(req != null, "红字开票请求不能为空");
        RedApply apply = locateRedApply(req);
        if (apply == null) {
            throw new GatewayException(InvoiceErrorCode.RED_APPLY_NOT_FOUND, "红字确认单不存在");
        }
        assertRedIssueAllowed(apply);

        Invoice original = rt.invoiceStore.findById(apply.getOriginalInvoiceId());
        ChannelConfig config = configOf(apply.getChannelCode(), apply.getAccountKey());
        Invoice red = createRedInvoice(apply, original, config, req.getNotifyUrl());
        InvoiceContext ctx = InvoiceContext.of(config, null, red).setRedApply(apply);
        InvoiceResult outcome = rt.registry.redHandler(apply.getChannelCode()).issueRed(ctx);
        return settleRedIssue(red, outcome, ctx);
    }

    /** 红字开票前置门禁：确认单须处于可开具状态且未过期。 */
    private void assertRedIssueAllowed(RedApply apply) {
        if (apply.getState() == RedApplyState.EXPIRED) {
            throw new GatewayException(InvoiceErrorCode.RED_CONFIRM_EXPIRED, "红字确认单已过期");
        }
        if (apply.getState() == RedApplyState.REJECTED) {
            throw new GatewayException(InvoiceErrorCode.RED_CONFIRM_REJECTED, "红字确认单被对方拒绝");
        }
        if (!apply.getState().canIssueRed()) {
            throw new GatewayException(InvoiceErrorCode.RED_CONFIRM_PENDING,
                    "红字确认单尚未确认，当前状态={}", apply.getState());
        }
        if (apply.getConfirmDeadline() != null && !rt.now().isBefore(apply.getConfirmDeadline())) {
            updateRedApply(apply, new RedApplyResult().setState(RedApplyState.EXPIRED));
            throw new GatewayException(InvoiceErrorCode.RED_CONFIRM_EXPIRED, "红字确认单已过期");
        }
    }

    /** 建立红字发票单据并推进到提交中。 */
    private Invoice createRedInvoice(RedApply apply, Invoice original, ChannelConfig config,
                                     String notifyUrl) {
        LocalDateTime now = rt.now();
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("RED_INVOICE", "true");
        Invoice red = new Invoice()
                .setInvoiceId(InvoiceIds.invoice())
                .setAppId(apply.getAppId())
                .setBizOrderNo(apply.getBizRedNo() + "-RED")
                .setChannelCode(apply.getChannelCode())
                .setChannelInstanceId(apply.getChannelInstanceId())
                .setAccountKey(apply.getAccountKey())
                .setSellerTaxNo(original == null ? null : original.getSellerTaxNo())
                .setSellerName(original == null ? null : original.getSellerName())
                .setBuyerTaxNo(original == null ? null : original.getBuyerTaxNo())
                .setBuyerTitle(original == null ? null : original.getBuyerTitle())
                .setInvoiceType(original == null ? null : original.getInvoiceType())
                .setItems(original == null ? null : original.getItems())
                .setAmount(apply.getAmount())
                .setOriginalInvoiceId(apply.getOriginalInvoiceId())
                .setRedApplyId(apply.getRedApplyId())
                .setNotifyUrl(notifyUrl)
                .setState(InvoiceState.CREATED)
                .setVersion(0L)
                .setCreatedTime(now)
                .setUpdatedTime(now)
                .setAttrs(attrs);
        rt.invoiceStore.save(red);
        rt.publish(InvoiceEvent.Type.CREATED, red, null, "红字发票单据建单完成");
        transition(red, InvoiceState.SUBMITTING, null);
        return red;
    }

    /** 归一渠道红票结果；确认单与原票的联动由 {@link #applyOutcome} 在成功时统一处理。 */
    private InvoiceResult settleRedIssue(Invoice red, InvoiceResult outcome, InvoiceContext ctx) {
        applyOutcome(red, outcome, ctx);
        return buildResult(red);
    }

    /**
     * 红票开具成功后联动确认单与原票状态（同步/异步回调统一走此处，幂等）。
     *
     * <p>蓝票单据不处理；确认单已置 ISSUED 时视为联动已完成直接返回。原票兼容
     * 快捷红冲留下的 RED_APPLYING 与两段式留下的 RED_APPLY_CONFIRMED 两种中间态，
     * 均按状态机逐级推进到 RED_SUCCEEDED。</p>
     */
    private void settleRedEffects(Invoice red) {
        Map<String, String> attrs = red.getAttrs();
        String redApplyId = red.getRedApplyId();
        if (attrs == null || !"true".equals(attrs.get("RED_INVOICE"))
                || StringUtils.isBlank(redApplyId)) {
            return;
        }
        RedApply apply = rt.redApplyStore.findById(redApplyId);
        if (apply == null) {
            return;
        }
        // 确认单联动：已 ISSUED 视为完成，跳过 CAS；原票联动在下方独立按状态机幂等推进，
        // 以便补上「确认单已置 ISSUED 但原票未联动」的历史中间态。
        if (apply.getState() != RedApplyState.ISSUED) {
            long expected = apply.getVersion();
            apply.setRedInvoiceId(red.getInvoiceId())
                    .setState(RedApplyState.ISSUED)
                    .setUpdatedTime(rt.now())
                    .setVersion(expected + 1);
            if (!rt.redApplyStore.tryUpdate(apply, expected)) {
                throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                        "红字确认单并发冲突: {}", redApplyId);
            }
        }
        Invoice original = rt.invoiceStore.findById(apply.getOriginalInvoiceId());
        if (original == null) {
            return;
        }
        InvoiceState state = original.getState();
        if (state == InvoiceState.RED_APPLYING) {
            transition(original, InvoiceState.RED_APPLY_CONFIRMED, null);
            transition(original, InvoiceState.RED_SUCCEEDED, null);
        } else if (state == InvoiceState.RED_APPLY_CONFIRMED) {
            transition(original, InvoiceState.RED_SUCCEEDED, null);
        }
    }

    private RedApply locateRedApply(RedIssueRequest req) {
        if (StringUtils.isNotBlank(req.getRedApplyId())) {
            return rt.redApplyStore.findById(req.getRedApplyId());
        }
        if (StringUtils.isNotBlank(req.getBizRedNo())) {
            return rt.redApplyStore.findByBizRedNo(req.getAppId(), req.getBizRedNo());
        }
        GatewayAssert.param(false, "需提供 redApplyId 或 bizRedNo");
        return null;
    }

    // ==================== 辅助能力：交付 / 修复 ====================

    /** 重新交付发票（短信/邮箱）；不改变单据状态，仅返回渠道受理结果。 */
    public DeliveryResult resend(DeliveryRequest req) {
        GatewayAssert.param(req != null, "交付请求不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getInvoiceId()), "invoiceId 不能为空");
        if (StringUtils.isBlank(req.getPhone()) && StringUtils.isBlank(req.getEmail())) {
            throw new GatewayException(InvoiceErrorCode.DELIVERY_TARGET_REQUIRED,
                    "手机号或邮箱至少提供一个");
        }
        Invoice invoice = rt.invoiceStore.findById(req.getInvoiceId());
        if (invoice == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND,
                    "发票单据不存在: {}", req.getInvoiceId());
        }
        ChannelConfig config = configOf(invoice.getChannelCode(), invoice.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        DeliveryResult result = rt.registry.deliverer(invoice.getChannelCode()).resend(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道交付未返回结果");
        }
        return result;
    }

    /** 提交发票修复申请（渠道侧补偿，不改单据状态）。 */
    public RepairResult repair(RepairRequest req) {
        GatewayAssert.param(req != null, "修复请求不能为空");
        Invoice invoice = locateForRepair(req);
        String channelCode = channelOf(req, invoice);
        ChannelConfig config = configForRepair(req, invoice, channelCode);
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        RepairResult result = rt.registry.repairer(channelCode).apply(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道修复未返回结果");
        }
        return result;
    }

    /** 查询发票修复结果（使用与申请相同的 channelOrderNo）。 */
    public RepairResult queryRepair(RepairRequest req) {
        GatewayAssert.param(req != null, "修复结果查询请求不能为空");
        if (StringUtils.isBlank(req.getChannelOrderNo())) {
            throw new GatewayException(InvoiceErrorCode.REPAIR_PARAM_REQUIRED,
                    "channelOrderNo 不能为空");
        }
        Invoice invoice = locateForRepair(req);
        String channelCode = channelOf(req, invoice);
        ChannelConfig config = configForRepair(req, invoice, channelCode);
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        RepairResult result = rt.registry.repairer(channelCode).query(ctx, req);
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道修复结果查询未返回结果");
        }
        return result;
    }

    private Invoice locateForRepair(RepairRequest req) {
        if (StringUtils.isNotBlank(req.getInvoiceId())) {
            return rt.invoiceStore.findById(req.getInvoiceId());
        }
        if (StringUtils.isNotBlank(req.getInvoiceNo())) {
            return rt.invoiceStore.findByInvoiceNo(req.getInvoiceNo(), req.getInvoiceCode());
        }
        return null;
    }

    /** 修复可无本地单据（按票号/日期补拉），此时用请求指定的渠道编码。 */
    private String channelOf(RepairRequest req, Invoice invoice) {
        if (invoice != null) {
            return invoice.getChannelCode();
        }
        if (StringUtils.isBlank(req.getChannelCode())) {
            throw new GatewayException(InvoiceErrorCode.REPAIR_PARAM_REQUIRED,
                    "无本地单据时需指定 channelCode");
        }
        return req.getChannelCode();
    }

    /** 有单据走账户键反查；无单据按 appId + channelCode + sellerTaxNo 定位配置。 */
    private ChannelConfig configForRepair(RepairRequest req, Invoice invoice, String channelCode) {
        if (invoice != null) {
            return configOf(invoice.getChannelCode(), invoice.getAccountKey());
        }
        if (StringUtils.isBlank(req.getAppId()) || StringUtils.isBlank(req.getSellerTaxNo())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "无本地单据的修复需提供 appId 与 sellerTaxNo 以定位渠道配置");
        }
        return rt.configProvider.getConfig(req.getAppId(), channelCode, req.getSellerTaxNo());
    }

    private Invoice locateOriginal(RedApplyRequest req) {
        if (StringUtils.isNotBlank(req.getOriginalInvoiceId())) {
            return rt.invoiceStore.findById(req.getOriginalInvoiceId());
        }
        if (StringUtils.isNotBlank(req.getOriginalInvoiceNo())) {
            return rt.invoiceStore.findByInvoiceNo(req.getOriginalInvoiceNo(),
                    req.getOriginalInvoiceCode());
        }
        GatewayAssert.param(false, "需提供 originalInvoiceId 或 originalInvoiceNo");
        return null;
    }

    private void updateRedApply(RedApply apply, RedApplyResult result) {
        if (result == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED, "渠道红字申请未返回结果");
        }
        long expected = apply.getVersion();
        if (result.getChannelOrderNo() != null) {
            apply.setChannelOrderNo(result.getChannelOrderNo());
        }
        if (result.getRedConfirmNo() != null) {
            apply.setRedConfirmNo(result.getRedConfirmNo());
        }
        if (result.getConfirmDeadline() != null) {
            apply.setConfirmDeadline(result.getConfirmDeadline());
        }
        if (result.getState() != null) {
            apply.setState(result.getState());
            if (result.getState() == RedApplyState.CONFIRMED) {
                apply.setConfirmedTime(rt.now());
            }
        }
        apply.setErrorCode(result.getErrorCode())
                .setErrorMsg(result.getErrorMsg())
                .setUpdatedTime(rt.now())
                .setVersion(expected + 1);
        if (!rt.redApplyStore.tryUpdate(apply, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "红字确认单并发冲突: {}", apply.getRedApplyId());
        }
    }

    // ==================== 渠道回调 ====================

    /** 回调归一：验签后既可能推进蓝票，也可能推进红字确认单。 */
    public InvoiceResult handleCallback(InvoiceCallbackRequest raw) {
        GatewayAssert.param(raw != null, "回调报文不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(raw.getChannelCode()), "回调缺少 channelCode");

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
        rt.publish(InvoiceEvent.Type.CALLBACK_RECEIVED, null, null, "收到渠道回调: " + code);
        final String appId = config.getAppId();

        Invoice touched = null;
        if (event.getRedState() != null) {
            RedApply apply = locateRedApplyByEvent(event, appId);
            if (apply != null) {
                RedApplyResult result = new RedApplyResult()
                        .setState(event.getRedState())
                        .setRedConfirmNo(event.getRedConfirmNo())
                        .setErrorCode(event.getErrorCode())
                        .setErrorMsg(event.getErrorMsg());
                updateRedApply(apply, result);
                Invoice original = rt.invoiceStore.findById(apply.getOriginalInvoiceId());
                if (event.getRedState() == RedApplyState.CONFIRMED && original != null
                        && original.getState() == InvoiceState.RED_APPLYING) {
                    transition(original, InvoiceState.RED_APPLY_CONFIRMED, null);
                }
                touched = original;
            }
        }
        if (event.getState() != null) {
            Invoice invoice = locateInvoiceByEvent(event, appId);
            if (invoice != null) {
                InvoiceResult outcome = callbackToResult(event);
                applyOutcome(invoice, outcome, InvoiceContext.of(config, null, invoice));
                touched = invoice;
            }
        } else if (hasSupplement(event)) {
            // 补充型回调：渠道把「票号/结果」与「版式文件地址」拆成两次通知，
            // 且先后顺序不固定；此类回调只合并票面字段/归档文件，不迁移状态，
            // 避免先到的票号被后到的版式回调抹掉。
            Invoice invoice = locateInvoiceByEvent(event, appId);
            if (invoice != null) {
                supplement(invoice, event);
                touched = invoice;
            }
        }
        return touched == null ? null : buildResult(touched);
    }

    private boolean hasSupplement(InvoiceCallbackEvent event) {
        return event.getInvoiceNo() != null || event.getInvoiceCode() != null
                || event.getInvoiceDate() != null || event.getChannelOrderNo() != null
                || (event.getFiles() != null && !event.getFiles().isEmpty());
    }

    /** 仅合并回调带来的票面字段与版式文件，不改单据状态；写入同样走 CAS。 */
    private void supplement(Invoice invoice, InvoiceCallbackEvent event) {
        List<InvoiceFile> files = normalizeFiles(event.getFiles(), invoice.getInvoiceId());
        if (!files.isEmpty()) {
            // 回调补充场景不阻断：固化失败保留 url，由 fileBytes/修复兜底
            fileMaterializer.materialize(files, false);
            rt.fileStore.saveAll(files);
        }
        long expected = invoice.getVersion();
        if (event.getInvoiceNo() != null) {
            invoice.setInvoiceNo(event.getInvoiceNo());
        }
        if (event.getInvoiceCode() != null) {
            invoice.setInvoiceCode(event.getInvoiceCode());
        }
        if (event.getInvoiceDate() != null) {
            invoice.setInvoiceDate(event.getInvoiceDate());
        }
        if (event.getChannelOrderNo() != null) {
            invoice.setChannelOrderNo(event.getChannelOrderNo());
        }
        invoice.setUpdatedTime(rt.now()).setVersion(expected + 1);
        if (!rt.invoiceStore.tryUpdate(invoice, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "回调票面字段合并并发冲突: {}", invoice.getInvoiceId());
        }
    }

    private InvoiceResult callbackToResult(InvoiceCallbackEvent event) {
        return new InvoiceResult()
                .setState(event.getState())
                .setChannelOrderNo(event.getChannelOrderNo())
                .setInvoiceNo(event.getInvoiceNo())
                .setInvoiceCode(event.getInvoiceCode())
                .setInvoiceDate(event.getInvoiceDate())
                .setAmount(event.getAmount())
                .setTaxAmount(event.getTaxAmount())
                .setFiles(event.getFiles())
                .setAuthChallenge(challengeFrom(event))
                .setErrorCode(event.getErrorCode())
                .setErrorMsg(event.getErrorMsg())
                .setRawPayload(event.getRawPayload());
    }

    /** 回调携带授权提示时构造挑战，避免只落到通用默认值（扫脸/登录/短信语义会丢失）。 */
    private AuthChallenge challengeFrom(InvoiceCallbackEvent event) {
        Map<String, String> attrs = event.getAttrs();
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        String type = attrs.get("authType");
        String text = attrs.get("challengeText");
        if (StringUtils.isBlank(type) && StringUtils.isBlank(text)) {
            return null;
        }
        AuthType authType = AuthType.SMS;
        if (StringUtils.isNotBlank(type)) {
            try {
                authType = AuthType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                authType = AuthType.SMS;
            }
        }
        return new AuthChallenge().setAuthType(authType)
                .setChallengeText(StringUtils.isBlank(text) ? "请完成渠道授权后继续开票" : text)
                .setChallengeUrl(attrs.get("challengeUrl"));
    }

    private Invoice locateInvoiceByEvent(InvoiceCallbackEvent event, String appId) {
        if (StringUtils.isNotBlank(event.getInvoiceId())) {
            return rt.invoiceStore.findById(event.getInvoiceId());
        }
        if (StringUtils.isNotBlank(event.getChannelOrderNo())) {
            Invoice invoice = rt.invoiceStore.findByChannelOrderNo(event.getChannelOrderNo());
            if (invoice != null) {
                return invoice;
            }
        }
        if (StringUtils.isNotBlank(event.getBizOrderNo())) {
            return rt.invoiceStore.findByBizOrderNo(appId, event.getBizOrderNo());
        }
        return null;
    }

    private RedApply locateRedApplyByEvent(InvoiceCallbackEvent event, String appId) {
        if (StringUtils.isNotBlank(event.getRedApplyId())) {
            return rt.redApplyStore.findById(event.getRedApplyId());
        }
        if (StringUtils.isNotBlank(event.getBizOrderNo())) {
            return rt.redApplyStore.findByBizRedNo(appId, event.getBizOrderNo());
        }
        return null;
    }

    // ==================== 渠道结果归一 ====================

    /** 把渠道返回的四态结果落到单据，并执行状态机迁移 + CAS 写入。 */
    private void applyOutcome(Invoice invoice, InvoiceResult outcome, InvoiceContext ctx) {
        if (outcome == null || outcome.getState() == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "渠道未返回明确单据状态: invoiceId={}", invoice.getInvoiceId());
        }
        final InvoiceResult result = outcome;
        InvoiceState target = outcome.getState();
        switch (target) {
            case WAITING_AUTH: {
                AuthChallenge challenge = ensureChallenge(outcome.getAuthChallenge(), invoice);
                final AuthSession session = ensureAuthSession(invoice, challenge);
                transition(invoice, InvoiceState.WAITING_AUTH, new Consumer<Invoice>() {
                    @Override
                    public void accept(Invoice inv) {
                        inv.setAuthSessionId(session.getSessionId());
                    }
                });
                break;
            }
            case PROCESSING:
                transition(invoice, InvoiceState.PROCESSING, new Consumer<Invoice>() {
                    @Override
                    public void accept(Invoice inv) {
                        if (result.getChannelOrderNo() != null) {
                            inv.setChannelOrderNo(result.getChannelOrderNo());
                        }
                    }
                });
                break;
            case SUCCEEDED: {
                assertAmountMatch(invoice, outcome);
                List<InvoiceFile> files = resolveFiles(outcome, ctx, invoice);
                // 归档即固化：url 型先下载转内联（strict：XML 失败阻断置成功）
                fileMaterializer.materialize(files, true);
                assertLegalXml(files, invoice.getInvoiceId());
                rt.fileStore.saveAll(files);
                transition(invoice, InvoiceState.SUCCEEDED, new Consumer<Invoice>() {
                    @Override
                    public void accept(Invoice inv) {
                        if (result.getChannelOrderNo() != null) {
                            inv.setChannelOrderNo(result.getChannelOrderNo());
                        }
                        // 仅在回调确实携带时覆盖：版式回调（qdbswj）可能先于票号回调（fpkj）到达，
                        // 无条件写入会把后续补充的票号抹成 null。
                        if (result.getInvoiceNo() != null) {
                            inv.setInvoiceNo(result.getInvoiceNo());
                        }
                        if (result.getInvoiceCode() != null) {
                            inv.setInvoiceCode(result.getInvoiceCode());
                        }
                        if (result.getInvoiceDate() != null) {
                            inv.setInvoiceDate(result.getInvoiceDate());
                        }
                        inv.setInvoiceTime(rt.now());
                    }
                });
                // 红票成功时联动确认单 ISSUED 与原票 RED_SUCCEEDED（同步/异步共用）
                settleRedEffects(invoice);
                break;
            }
            case FAILED:
                transition(invoice, InvoiceState.FAILED, new Consumer<Invoice>() {
                    @Override
                    public void accept(Invoice inv) {
                        inv.setErrorCode(result.getErrorCode()).setErrorMsg(result.getErrorMsg());
                        if (result.getChannelOrderNo() != null) {
                            inv.setChannelOrderNo(result.getChannelOrderNo());
                        }
                    }
                });
                break;
            default:
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "渠道返回了不可归一的单据状态: {}", target);
        }
    }

    /**
     * 按类型取已固化的文件字节（统一出口，使用方无需感知渠道交付形态）。
     *
     * <p>content 已内联 → 直接解码返回；只有 url → 下载并回写 FileStore（幂等，
     * 第二次调用不再下载）；url 失效 → 自动降级调渠道 {@code InvoiceFileFetcher}
     * 重拉一次，仍失败抛 {@code FILE_URL_EXPIRED} 引导走修复。</p>
     */
    public byte[] fileBytes(String invoiceId, InvoiceFileType type) {
        GatewayAssert.param(StringUtils.isNotBlank(invoiceId), "invoiceId 不能为空");
        GatewayAssert.param(type != null, "文件类型不能为空");
        Invoice invoice = rt.invoiceStore.findById(invoiceId);
        if (invoice == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND,
                    "发票单据不存在: {}", invoiceId);
        }
        List<InvoiceFile> files = rt.fileStore.findByInvoiceIdAndType(invoiceId, type);
        InvoiceFile target = pickFile(files);
        GatewayAssert.isTrue(target != null, InvoiceErrorCode.FILE_NOT_FOUND,
                "单据未归档 {} 版式文件: invoiceId={}", type, invoiceId);
        try {
            return fileMaterializer.materializeBytes(target);
        } catch (GatewayException e) {
            if (e.getErrorCode() != InvoiceErrorCode.FILE_URL_EXPIRED) {
                throw e;
            }
        }
        // url 失效：渠道重拉一次（严格模式：取不到字节继续抛 FILE_URL_EXPIRED）
        ChannelConfig config = configOf(invoice.getChannelCode(), invoice.getAccountKey());
        InvoiceContext ctx = InvoiceContext.of(config, null, invoice);
        List<InvoiceFile> refetched = normalizeFiles(rt.registry.fileFetcher(invoice.getChannelCode())
                .fetch(ctx, EnumSet.of(type)), invoiceId);
        fileMaterializer.materialize(refetched, true);
        rt.fileStore.saveAll(refetched);
        InvoiceFile refreshed = pickFile(refetched);
        GatewayAssert.isTrue(refreshed != null && StringUtils.isNotBlank(refreshed.getContent()),
                InvoiceErrorCode.FILE_URL_EXPIRED,
                "渠道重拉仍未取得可用文件: invoiceId={}, type={}", invoiceId, type);
        return fileMaterializer.materializeBytes(refreshed);
    }

    /** 优先已固化文件，其次任一有载荷的文件。 */
    private InvoiceFile pickFile(List<InvoiceFile> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        InvoiceFile fallback = null;
        for (InvoiceFile file : files) {
            if (file == null || file.getType() == null || !file.hasPayload()) {
                continue;
            }
            if (file.isMaterialized()) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    private List<InvoiceFile> resolveFiles(InvoiceResult outcome, InvoiceContext ctx, Invoice invoice) {        List<InvoiceFile> files = outcome.getFiles();
        if (files == null || files.isEmpty()) {
            files = rt.registry.fileFetcher(invoice.getChannelCode())
                    .fetch(ctx, EnumSet.of(InvoiceFileType.XML,
                            InvoiceFileType.OFD, InvoiceFileType.PDF));
        }
        return normalizeFiles(files, invoice.getInvoiceId());
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
            if (file.getCreatedTime() == null) {
                file.setCreatedTime(rt.now());
            }
            if (file.getDigest() == null && file.getContent() != null) {
                file.setDigest(SignUtil.sha256Hex(file.getContent()));
            }
            if (file.getSize() < 0 && file.getContent() != null) {
                file.setSize(file.getContent().length());
            }
            normalized.add(file);
        }
        return normalized;
    }

    private void assertLegalXml(List<InvoiceFile> files, String invoiceId) {
        for (InvoiceFile file : files) {
            if (file.getType() == InvoiceFileType.XML && file.hasPayload()) {
                return;
            }
        }
        throw new GatewayException(InvoiceErrorCode.XML_ORIGINAL_REQUIRED,
                "渠道回传开票成功但未归档数电票 XML 法定原件: invoiceId={}", invoiceId);
    }

    // ==================== 授权会话管理 ====================

    private AuthChallenge ensureChallenge(AuthChallenge raw, Invoice invoice) {
        AuthChallenge challenge = raw == null ? new AuthChallenge() : raw;
        if (StringUtils.isBlank(challenge.getSessionId())) {
            challenge.setSessionId(InvoiceIds.authSession());
        }
        if (challenge.getAuthType() == null) {
            challenge.setAuthType(AuthType.SMS);
        }
        if (challenge.getExpireTime() == null) {
            challenge.setExpireTime(rt.now().plusMinutes(AUTH_SESSION_MINUTES));
        }
        if (challenge.getChallengeText() == null) {
            challenge.setChallengeText("请完成渠道授权后继续开票");
        }
        return challenge;
    }

    private AuthSession ensureAuthSession(Invoice invoice, AuthChallenge challenge) {
        AuthSession session = invoice.getAuthSessionId() == null ? null
                : rt.authSessionStore.findById(invoice.getAuthSessionId());
        boolean created = session == null;
        if (created) {
            session = new AuthSession().setCreatedTime(rt.now());
        }
        long expected = session.getVersion();
        session.setSessionId(challenge.getSessionId())
                .setInvoiceId(invoice.getInvoiceId())
                .setAppId(invoice.getAppId())
                .setChannelCode(invoice.getChannelCode())
                .setAccountKey(invoice.getAccountKey())
                .setAuthType(challenge.getAuthType())
                .setState(AuthSessionState.PENDING)
                .setChallengeUrl(challenge.getChallengeUrl())
                .setChallengeText(challenge.getChallengeText())
                .setExpireTime(challenge.getExpireTime())
                .setPayload(challenge.getExtra())
                .setUpdatedTime(rt.now())
                .setVersion(expected + 1);
        if (created) {
            rt.authSessionStore.save(session);
        } else if (!rt.authSessionStore.tryUpdate(session, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "授权会话并发冲突: {}", session.getSessionId());
        }
        return session;
    }

    private void updateSessionState(AuthSession session, AuthSessionState state) {
        long expected = session.getVersion();
        session.setState(state).setUpdatedTime(rt.now()).setVersion(expected + 1);
        if (!rt.authSessionStore.tryUpdate(session, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "授权会话并发冲突: {}", session.getSessionId());
        }
    }

    private void applyChallengeToSession(AuthSession session, AuthChallenge challenge) {
        if (challenge == null) {
            return;
        }
        long expected = session.getVersion();
        session.setAuthType(challenge.getAuthType() == null
                        ? session.getAuthType() : challenge.getAuthType())
                .setChallengeUrl(challenge.getChallengeUrl())
                .setChallengeText(challenge.getChallengeText())
                .setExpireTime(challenge.getExpireTime() == null
                        ? session.getExpireTime() : challenge.getExpireTime())
                .setPayload(challenge.getExtra() == null
                        ? session.getPayload() : challenge.getExtra())
                .setState(AuthSessionState.PENDING)
                .setUpdatedTime(rt.now())
                .setVersion(expected + 1);
        if (!rt.authSessionStore.tryUpdate(session, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "授权会话并发冲突: {}", session.getSessionId());
        }
    }

    // ==================== 状态迁移（CAS） ====================

    /**
     * 唯一状态迁移入口：校验合法性 → 应用附加字段 → CAS 写入 → 发布事件。
     *
     * @return 是否实际发生迁移（同态重复回调返回 false）
     */
    private boolean transition(Invoice invoice, InvoiceState target, Consumer<Invoice> mutator) {
        if (invoice.getState() == target) {
            return false;
        }
        InvoiceStateMachine.assertTransition(invoice.getState(), target);
        if (mutator != null) {
            mutator.accept(invoice);
        }
        invoice.setState(target).setUpdatedTime(rt.now());
        long expected = invoice.getVersion();
        invoice.setVersion(expected + 1);
        if (!rt.invoiceStore.tryUpdate(invoice, expected)) {
            throw new GatewayException(InvoiceErrorCode.STORE_CONCURRENT_MODIFICATION,
                    "状态迁移并发冲突: invoiceId={}, expectedVersion={}, target={}",
                    invoice.getInvoiceId(), expected, target);
        }
        rt.publish(eventTypeOf(target), invoice, null, "状态迁移 -> " + target);
        return true;
    }

    private InvoiceEvent.Type eventTypeOf(InvoiceState target) {
        switch (target) {
            case WAITING_AUTH:
                return InvoiceEvent.Type.WAITING_AUTH;
            case PROCESSING:
                return InvoiceEvent.Type.PROCESSING;
            case SUCCEEDED:
                return InvoiceEvent.Type.SUCCEEDED;
            case FAILED:
                return InvoiceEvent.Type.FAILED;
            case SUBMITTING:
                return InvoiceEvent.Type.SUBMITTED;
            case RED_APPLYING:
                return InvoiceEvent.Type.RED_APPLIED;
            case RED_APPLY_CONFIRMED:
                return InvoiceEvent.Type.RED_CONFIRMED;
            case RED_SUCCEEDED:
                return InvoiceEvent.Type.RED_ISSUED;
            default:
                return InvoiceEvent.Type.CREATED;
        }
    }

    // ==================== 结果构造与校验 ====================

    private InvoiceResult buildResult(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        List<InvoiceFile> files = rt.fileStore.findByInvoiceId(invoice.getInvoiceId());
        AuthChallenge challenge = null;
        if (invoice.getState() == InvoiceState.WAITING_AUTH
                && StringUtils.isNotBlank(invoice.getAuthSessionId())) {
            AuthSession session = rt.authSessionStore.findById(invoice.getAuthSessionId());
            if (session != null) {
                challenge = new AuthChallenge()
                        .setSessionId(session.getSessionId())
                        .setAuthType(session.getAuthType())
                        .setChallengeUrl(session.getChallengeUrl())
                        .setChallengeText(session.getChallengeText())
                        .setExpireTime(session.getExpireTime())
                        .setExtra(session.getPayload());
            }
        }
        return InvoiceResult.fromInvoice(invoice, files, challenge);
    }

    private ChannelConfig configOf(String channelCode, String accountKey) {
        if (StringUtils.isBlank(accountKey)) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "单据缺少账户键，无法反查渠道配置: channel={}", channelCode);
        }
        return rt.configProvider.getByAccountKey(channelCode, accountKey);
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

    private void validateRedRequest(RedApplyRequest req) {
        GatewayAssert.param(req != null, "红冲申请不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getAppId()), "appId 不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getBizRedNo()), "bizRedNo 不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getReason()), "红冲原因不能为空");
        GatewayAssert.param(StringUtils.isNotBlank(req.getOriginalInvoiceId())
                        || StringUtils.isNotBlank(req.getOriginalInvoiceNo()),
                "需提供 originalInvoiceId 或 originalInvoiceNo");
    }

    private void assertIdempotent(Invoice existing, InvoiceRequest req) {
        boolean same = Objects.equals(existing.getSellerTaxNo(), req.getSellerTaxNo())
                && Objects.equals(existing.getBuyerTitle(), req.getBuyerTitle())
                && existing.getInvoiceType() == req.getInvoiceType()
                && existing.getAmount() != null && req.getAmount() != null
                && existing.getAmount().compareTo(req.getAmount()) == 0;
        if (!same) {
            throw new GatewayException(InvoiceErrorCode.DUPLICATE_BIZ_ORDER,
                    "业务单号[{}]已存在且参数不一致", req.getBizOrderNo());
        }
    }

    private void assertAmountMatch(Invoice invoice, InvoiceResult outcome) {
        if (outcome.getAmount() == null || invoice.getAmount() == null) {
            return;
        }
        if (outcome.getAmount().subtract(invoice.getAmount()).abs()
                .compareTo(AMOUNT_TOLERANCE) > 0) {
            throw new GatewayException(InvoiceErrorCode.AMOUNT_MISMATCH,
                    "渠道回传金额[{}]与单据金额[{}]不一致",
                    outcome.getAmount(), invoice.getAmount());
        }
    }
}
