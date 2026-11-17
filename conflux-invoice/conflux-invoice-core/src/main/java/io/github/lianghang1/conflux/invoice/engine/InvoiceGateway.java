package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.support.PdfImageConverter;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.RedApply;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    /**
     * 快捷红冲：一次调用覆盖红冲全流程，用结果的 outcome 区分走到哪一步。
     *
     * <p>渠道无需确认时自动接续开具红字发票，返回 ACCEPTED（含红票受理结果）；
     * 需对方确认时返回 WAITING_CONFIRM 的确认单，由使用方后续确认并开具。</p>
     */
    public QuickRedResult quickRed(RedApplyRequest request) {
        return orchestrator.quickRed(request);
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

    /**
     * 取指定类型版式文件的原始字节（统一出口，屏蔽渠道差异）。
     *
     * <p>content 已内联 → 直接解码；只有 url → 下载并回写 FileStore（幂等）；
     * url 失效 → 自动降级渠道重拉一次。无论渠道给的是 Base64 还是下载地址，
     * 使用方从这里拿到的永远是字节。</p>
     */
    public byte[] fileBytes(String invoiceId, InvoiceFileType type) {
        return orchestrator.fileBytes(invoiceId, type);
    }

    /**
     * 【按需能力】版式 PDF 逐页转图（防丢字/预览场景）。
     *
     * <p>直接转发 {@link PdfImageConverter#convert}：默认保持原始页面尺寸；
     * 传 {@code config.targetPaper} 可强制统一纸型。转换时机与参数完全由使用方
     * 决定，框架不做任何自动转换。</p>
     */
    public List<PdfImageConverter.PageImage> pageImages(String invoiceId,
                                                        PdfImageConverter.Config config) {
        try {
            return PdfImageConverter.convert(fileBytes(invoiceId, InvoiceFileType.PDF), config);
        } catch (IOException e) {
            throw new GatewayException(InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                    "PDF 转图失败: invoiceId={}", invoiceId, e);
        }
    }

    /**
     * 【按需能力】版式 PDF 打印适配：逐页转图后重组为指定纸型的 PDF
     * （真实场景：PDF 直接打印丢字 → 栅格化成图片再排进 A4，文字固化为像素）。
     *
     * <p>等价于 {@code PdfImageConverter.rasterizeToPdf}：输出仍是单一 PDF 字节，
     * 「一个文件进出」的打印接口零改动；{@code paper} 传 null 保持每页原始尺寸，
     * 传 {@code PaperSize.A4} 则全部页面等比缩放放进 A4（不裁切不变形）。</p>
     */
    public byte[] printAdaptedPdf(String invoiceId, PdfImageConverter.PaperSize paper, int dpi) {
        PdfImageConverter.Config config = new PdfImageConverter.Config();
        config.dpi = dpi;
        if (paper != null) {
            config.targetPaper = paper;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfImageConverter.rasterizeToPdf(fileBytes(invoiceId, InvoiceFileType.PDF), out, config);
            return out.toByteArray();
        } catch (IOException e) {
            throw new GatewayException(InvoiceErrorCode.FILE_MATERIALIZE_FAILED,
                    "PDF 打印适配失败: invoiceId={}", invoiceId, e);
        }
    }

    public Invoice findById(String invoiceId) {
        return runtime.invoiceStore.findById(invoiceId);
    }

    public RedApply findRedApply(String redApplyId) {
        return runtime.redApplyStore.findById(redApplyId);
    }
}
