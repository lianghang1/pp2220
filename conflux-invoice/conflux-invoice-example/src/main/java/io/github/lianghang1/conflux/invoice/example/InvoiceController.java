package io.github.lianghang1.conflux.invoice.example;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示 REST 入口：完整暴露蓝字开票、查单、授权提交、两段红冲、渠道回调、文件查询。
 *
 * <p>回调地址需在渠道侧带上 {@code ?accountKey=...} 查询参数，供验签前反查配置
 * （见 {@code InvoiceAccountKey}）。</p>
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

    /** 查单（在途会顺带向渠道查询推进状态）。 */
    @GetMapping("/{invoiceId}")
    public InvoiceResult query(@PathVariable String invoiceId) {
        return gateway.query(new InvoiceQueryRequest().setInvoiceId(invoiceId));
    }

    /** 提交授权凭证。 */
    @PostMapping("/auth")
    public InvoiceResult submitAuth(@RequestBody AuthSubmission submission) {
        return gateway.submitAuth(submission);
    }

    /** 申请红字确认单（第一段）。 */
    @PostMapping("/red/apply")
    public RedApply applyRed(@RequestBody RedApplyRequest request) {
        return gateway.applyRed(request);
    }

    /** 查询红字确认单确认状态。 */
    @PostMapping("/red/confirm/{redApplyId}")
    public RedApply confirmRed(@PathVariable String redApplyId) {
        return gateway.confirmRed(redApplyId);
    }

    /** 确认后开具红字发票（第二段）。 */
    @PostMapping("/red/issue")
    public InvoiceResult issueRed(@RequestBody RedIssueRequest request) {
        return gateway.issueRed(request);
    }

    /** 已归档版式文件（含 XML 法定原件）。 */
    @GetMapping("/{invoiceId}/files")
    public List<InvoiceFile> files(@PathVariable String invoiceId) {
        return gateway.files(invoiceId);
    }

    /** 渠道回调；按渠道协议返回应答报文。 */
    @PostMapping("/callback/{channelCode}")
    public ResponseEntity<String> callback(@PathVariable String channelCode,
                                           @RequestParam Map<String, String> queryParams,
                                           @RequestBody(required = false) String body) {
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest()
                .setChannelCode(channelCode)
                .setQueryParams(queryParams)
                .setBody(body == null ? "" : body);
        try {
            gateway.handleCallback(raw);
            InvoiceCallbackReply reply = gateway.callbackReply(channelCode, true, "SUCCESS");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(reply.getContentType()))
                    .body(reply.getBody());
        } catch (RuntimeException e) {
            InvoiceCallbackReply reply = gateway.callbackReply(channelCode, false, e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.parseMediaType(reply.getContentType()))
                    .body(reply.getBody());
        }
    }
}
