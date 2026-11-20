package io.github.lianghang1.conflux.invoice.example;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyQueryRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.support.PdfImageConverter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示 REST 入口：SDK 为无状态渠道聚合层，只做渠道调用与结果归一。
 *
 * <p>开票/查单返回的 {@code InvoiceResult.invoice} 是使用方需要自己保管的载体；
 * 后续授权、红冲、交付等把载体随请求回传。回调入口用 {@code parseCallback} 验签归一，
 * 使用方按业务单号定位自己的单据、判重、校验金额、推进业务状态，再应答渠道。</p>
 *
 * @author lianghang
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceGateway gateway;

    public InvoiceController(InvoiceGateway gateway) {
        this.gateway = gateway;
    }

    /** 蓝字开票。 */
    @PostMapping
    public InvoiceResult issue(@RequestBody InvoiceRequest request) {
        return gateway.issue(request);
    }

    /** 查单：传 configId + channelCode + 定位要素，或使用方回传 invoice 载体。 */
    @PostMapping("/query")
    public InvoiceResult query(@RequestBody InvoiceQueryRequest request) {
        return gateway.query(request);
    }

    /** 提交授权凭证：会话/发票载体由使用方回传。 */
    @PostMapping("/auth")
    public InvoiceResult submitAuth(@RequestBody AuthSubmission submission) {
        return gateway.submitAuth(submission);
    }

    /** 申请红字确认单（第一段）。 */
    @PostMapping("/red/apply")
    public RedApply applyRed(@RequestBody RedApplyRequest request) {
        return gateway.applyRed(request);
    }

    /** 快捷红冲。 */
    @PostMapping("/red/quick")
    public QuickRedResult quickRed(@RequestBody RedApplyRequest request) {
        return gateway.quickRed(request);
    }

    /** 查询红字确认单状态（确认单载体回传）。 */
    @PostMapping("/red/query")
    public RedApply queryRedApply(@RequestBody RedApplyQueryRequest request) {
        return gateway.queryRedApply(request);
    }

    /** 主动确认 / 拒绝红字确认单。 */
    @PostMapping("/red/confirm-action")
    public RedApply confirmRedAction(@RequestBody RedConfirmRequest request) {
        return gateway.confirmRed(request);
    }

    /** 确认后开具红字发票（第二段）。 */
    @PostMapping("/red/issue")
    public InvoiceResult issueRed(@RequestBody RedIssueRequest request) {
        return gateway.issueRed(request);
    }

    /** 版式文件就地固化（下载/解码为字节），返回同一批文件。 */
    @PostMapping("/files/materialize")
    public List<InvoiceFile> materializeFiles(@RequestBody List<InvoiceFile> files,
                                              @RequestParam String invoiceId) {
        return gateway.materializeFiles(files, invoiceId, true);
    }

    /** 取指定类型文件字节：从一批文件里挑目标类型并解码。 */
    @PostMapping(value = "/files/bytes", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> fileBytes(@RequestBody List<InvoiceFile> files,
                                            @RequestParam InvoiceFileType type) {
        return ResponseEntity.ok(gateway.fileBytes(files, type));
    }

    /** PDF 逐页转图（使用方自己拿到 PDF 字节后按需调用）。 */
    @PostMapping("/pdf/pages")
    public List<PdfImageConverter.PageImage> pageImages(@RequestBody byte[] pdfBytes,
                                                        @RequestBody(required = false) PdfImageConverter.Config config) {
        return gateway.pageImages(pdfBytes, config == null ? new PdfImageConverter.Config() : config);
    }

    /** PDF 打印适配。 */
    @PostMapping(value = "/pdf/print-adapted", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> printAdapted(@RequestBody byte[] pdfBytes,
                                               @RequestParam(required = false) PdfImageConverter.PaperSize paper,
                                               @RequestParam(defaultValue = "150") int dpi) {
        return ResponseEntity.ok(gateway.printAdaptedPdf(pdfBytes, paper, dpi));
    }

    /** 重新交付发票。 */
    @PostMapping("/delivery")
    public DeliveryResult resend(@RequestBody DeliveryRequest request) {
        return gateway.resend(request);
    }

    /** 提交发票修复申请。 */
    @PostMapping("/repair")
    public RepairResult repair(@RequestBody RepairRequest request) {
        return gateway.repair(request);
    }

    /** 查询发票修复结果。 */
    @PostMapping("/repair/query")
    public RepairResult queryRepair(@RequestBody RepairRequest request) {
        return gateway.queryRepair(request);
    }

    /**
     * 渠道回调入口：只做验签归一，业务处理（判重、金额校验、落库、状态推进）由使用方完成。
     */
    @PostMapping("/callback/{channelCode}")
    public InvoiceCallbackReply callback(@org.springframework.web.bind.annotation.PathVariable String channelCode,
                                          @org.springframework.web.bind.annotation.RequestParam Map<String, String> queryParams,
                                          @org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
        try {
            InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                    .setChannelCode(channelCode)
                    .setQueryParams(queryParams)
                    .setBody(body);
            // 实际业务应查自己 DB（用回调里的业务单号）定位 configId 与销方税号，这里演示从查询参数取
            String configId = queryParams == null ? null : queryParams.get("configId");
            String sellerTaxNo = queryParams == null ? null : queryParams.get("sellerTaxNo");
            InvoiceCallbackEvent event = gateway.parseCallback(configId, sellerTaxNo, raw);
            // 使用方在此按 event.getBizOrderNo() 定位自己的单据并推进业务，判重/金额校验自行完成
            return gateway.callbackReply(channelCode, true, "SUCCESS");
        } catch (Exception e) {
            // 处理失败以失败应答触发渠道重推
            return gateway.callbackReply(channelCode, false, e.getMessage());
        }
    }
}
