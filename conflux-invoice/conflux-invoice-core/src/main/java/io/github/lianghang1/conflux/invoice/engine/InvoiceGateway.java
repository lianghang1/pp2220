package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.RedApply;

import java.util.List;

/**
 * 发票网关门面：使用方唯一需要接触的入口。
 *
 * <p>薄门面，全部编排逻辑在 {@link InvoiceOrchestrator}；本类只做参数转交，
 * 便于将来在不改调用方的前提下替换编排实现。</p>
 *
 * @author lianghang
 */
public class InvoiceGateway {

    private final InvoiceRuntime runtime;
    private final InvoiceOrchestrator orchestrator;

    public InvoiceGateway(InvoiceRuntime runtime) {
        this.runtime = runtime;
        this.orchestrator = new InvoiceOrchestrator(runtime);
    }

    /** 蓝字发票开具（幂等：相同 appId + bizOrderNo 且参数一致时直接返回原单据）。 */
    public InvoiceResult issue(InvoiceRequest request) {
        return orchestrator.issue(request);
    }

    /** 查单；在途单据会顺带向渠道查询并推进状态。 */
    public InvoiceResult query(InvoiceQueryRequest request) {
        return orchestrator.query(request);
    }

    /** 强制查单刷新（补单/轮询扫描用）。 */
    public InvoiceResult refresh(String invoiceId) {
        return orchestrator.refresh(invoiceId);
    }

    /** 提交授权凭证（短信码/扫码 token/扫脸结果）。 */
    public InvoiceResult submitAuth(AuthSubmission submission) {
        return orchestrator.submitAuth(submission);
    }

    /** 申请红字确认单（两段红冲第一段）。 */
    public RedApply applyRed(RedApplyRequest request) {
        return orchestrator.applyRed(request);
    }

    /** 查询红字确认单确认状态（第二段之前的确认环节）。 */
    public RedApply confirmRed(String redApplyId) {
        return orchestrator.confirmRed(redApplyId);
    }

    /** 主动确认或拒绝红字确认单（渠道可选能力，与查询状态分开）。 */
    public RedApply confirmRed(RedConfirmRequest request) {
        return orchestrator.confirmRed(request);
    }

    /** 确认后开具红字发票（两段红冲第二段）。 */
    public InvoiceResult issueRed(RedIssueRequest request) {
        return orchestrator.issueRed(request);
    }

    /** 重新交付发票（短信/邮箱重发），不改单据状态。 */
    public DeliveryResult resend(DeliveryRequest request) {
        return orchestrator.resend(request);
    }

    /** 提交发票修复申请（渠道侧补偿，不改单据状态）。 */
    public RepairResult repair(RepairRequest request) {
        return orchestrator.repair(request);
    }

    /** 查询发票修复结果。 */
    public RepairResult queryRepair(RepairRequest request) {
        return orchestrator.queryRepair(request);
    }

    /** 处理渠道回调；返回被推进的单据结果（无匹配单据时返回 null）。 */
    public InvoiceResult handleCallback(InvoiceCallbackRequest request) {
        return orchestrator.handleCallback(request);
    }

    /** 生成渠道回调应答报文。 */
    public InvoiceCallbackReply callbackReply(String channelCode, boolean success, String message) {
        return runtime.registry.callbackHandler(channelCode).reply(success, message);
    }

    /** 已归档的版式文件（含法定 XML 原件）。 */
    public List<InvoiceFile> files(String invoiceId) {
        return runtime.fileStore.findByInvoiceId(invoiceId);
    }

    public Invoice findById(String invoiceId) {
        return runtime.invoiceStore.findById(invoiceId);
    }

    public RedApply findRedApply(String redApplyId) {
        return runtime.redApplyStore.findById(redApplyId);
    }
}
