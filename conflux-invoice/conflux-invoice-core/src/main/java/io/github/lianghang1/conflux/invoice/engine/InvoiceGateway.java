package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyQueryRequest;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.support.PdfImageConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * 发票网关门面：统一发票 API 的唯一入口，双形态共享——
 * 纯 jar 场景经 {@link #builder()} 手动组装，Spring Boot 场景由 starter 装配为单例 Bean。
 *
 * <p>SDK 为无状态渠道聚合层：统一出入参、统一结果归一、回调验签归一。
 * 不持有台账、不推进状态、不暂存会话——落库、状态推进、判重、金额校验、
 * 业务联动（订单/库存等）全部由使用方完成。票据载体随结果返回、随后续请求回传。</p>
 *
 * <p>接入契约：渠道受理成功不等于业务最终态，最终态来自渠道回调或查单，
 * 使用方必须实现回调入口或定时查单（至少其一）。</p>
 *
 * @author lianghang
 */
public class InvoiceGateway {

    private final InvoiceRuntime runtime;
    private final InvoiceOrchestrator orchestrator;

    private InvoiceGateway(Builder builder) {
        this.runtime = new InvoiceRuntime(builder.registry, builder.configProvider, builder.clock);
        this.orchestrator = new InvoiceOrchestrator(runtime, builder.fileOptions);
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 注册渠道提供者（启动期调用）。 */
    public void registerChannel(InvoiceChannelProvider provider) {
        runtime.registry.register(provider);
    }

    /** 已注册渠道编码集合。 */
    public java.util.Set<String> registeredChannels() {
        return runtime.registry.registeredChannels();
    }

    // ---------- 开票 / 查单 / 授权 ----------

    /** 蓝字发票开具：返回归一受理结果与发票载体。 */
    public InvoiceResult issue(InvoiceRequest request) {
        return orchestrator.issue(request);
    }

    /** 渠道查单：返回归一快照与发票载体，状态推进由使用方完成。 */
    public InvoiceResult query(InvoiceQueryRequest request) {
        return orchestrator.query(request);
    }

    /** 提交授权凭证：会话上下文随载体回传，授权通过后重新开票。 */
    public InvoiceResult submitAuth(AuthSubmission submission) {
        return orchestrator.submitAuth(submission);
    }

    // ---------- 红冲 ----------

    /** 申请红字确认单（两段红冲第一段）。 */
    public RedApply applyRed(RedApplyRequest request) {
        return orchestrator.applyRed(request);
    }

    /**
     * 快捷红冲：一次调用覆盖红冲全流程，用 outcome 区分走到哪一步。
     * 免确认时自动接续开具红票（ACCEPTED），需确认时返回 WAITING_CONFIRM 确认单。
     */
    public QuickRedResult quickRed(RedApplyRequest request) {
        return orchestrator.quickRed(request);
    }

    /** 查询红字确认单确认状态（确认单载体由使用方回传）。 */
    public RedApply queryRedApply(RedApplyQueryRequest request) {
        return orchestrator.queryRedApply(request);
    }

    /** 主动确认 / 拒绝红字确认单（渠道可选能力）。 */
    public RedApply confirmRed(RedConfirmRequest request) {
        return orchestrator.confirmRed(request);
    }

    /** 确认后开具红字发票（两段红冲第二段）。 */
    public InvoiceResult issueRed(RedIssueRequest request) {
        return orchestrator.issueRed(request);
    }

    // ---------- 辅助能力 ----------

    /** 重新交付发票（短信/邮箱重发），不改状态。 */
    public DeliveryResult resend(DeliveryRequest request) {
        return orchestrator.resend(request);
    }

    /** 提交发票修复申请（渠道侧补偿，不改状态）。 */
    public RepairResult repair(RepairRequest request) {
        return orchestrator.repair(request);
    }

    /** 查询发票修复结果。 */
    public RepairResult queryRepair(RepairRequest request) {
        return orchestrator.queryRepair(request);
    }

    // ---------- 渠道回调 ----------

    /**
     * 验签并解析渠道回调，返回归一化事件。使用方按事件中的业务单号定位自己的
     * 单据，判重、金额校验与状态推进自行完成后，将应答报文写回渠道。
     */
    public InvoiceCallbackEvent parseCallback(InvoiceCallbackRequest request) {
        return orchestrator.parseCallback(request);
    }

    /** 渠道认可的处理成功应答报文。 */
    public InvoiceCallbackReply callbackReply(String channelCode, boolean success, String message) {
        return runtime.registry.callbackHandler(channelCode).reply(success, message);
    }

    // ---------- 文件工具（纯能力，无台账） ----------

    /** 就地固化版式文件（下载/解码后写回文件对象），返回同一批文件。 */
    public List<InvoiceFile> materializeFiles(List<InvoiceFile> files, String invoiceId, boolean strict) {
        return orchestrator.materializeFiles(files, invoiceId, strict);
    }

    /** 取指定版式文件字节（内联解码；仅 url 时下载）。 */
    public byte[] fileBytes(InvoiceFile file) {
        return orchestrator.fileBytes(file);
    }

    /** 指定类型文件字节：从一批文件里挑出首个有载荷的目标类型文件。 */
    public byte[] fileBytes(List<InvoiceFile> files, InvoiceFileType type) {
        InvoiceFile target = pickFile(files, type);
        if (target == null) {
            throw new GatewayException(InvoiceErrorCode.FILE_NOT_FOUND,
                    "文件集合中未找到 {} 版式文件", type);
        }
        return fileBytes(target);
    }

    /** 【按需能力】版式 PDF 逐页转图。 */
    public List<PdfImageConverter.PageImage> pageImages(byte[] pdfBytes, PdfImageConverter.Config config) {
        try {
            return PdfImageConverter.convert(pdfBytes, config);
        } catch (IOException e) {
            throw new GatewayException(InvoiceErrorCode.FILE_MATERIALIZE_FAILED, "PDF 转图失败", e);
        }
    }

    /** 【按需能力】版式 PDF 打印适配：逐页转图后重组为指定纸型 PDF。 */
    public byte[] printAdaptedPdf(byte[] pdfBytes, PdfImageConverter.PaperSize paper, int dpi) {
        PdfImageConverter.Config config = new PdfImageConverter.Config();
        config.dpi = dpi;
        if (paper != null) {
            config.targetPaper = paper;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfImageConverter.rasterizeToPdf(pdfBytes, out, config);
            return out.toByteArray();
        } catch (IOException e) {
            throw new GatewayException(InvoiceErrorCode.FILE_MATERIALIZE_FAILED, "PDF 打印适配失败", e);
        }
    }

    private InvoiceFile pickFile(List<InvoiceFile> files, InvoiceFileType type) {
        if (files == null || type == null) {
            return null;
        }
        InvoiceFile fallback = null;
        for (InvoiceFile file : files) {
            if (file == null || file.getType() != type || !file.hasPayload()) {
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

    // ---------- 构建 ----------

    /** 网关构建器。 */
    public static class Builder {
        private final InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        private InvoiceConfigProvider configProvider;
        private Clock clock = Clock.systemDefaultZone();
        private io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions fileOptions;

        /** 注册渠道提供者。 */
        public Builder channel(InvoiceChannelProvider provider) {
            registry.register(provider);
            return this;
        }

        /** 渠道配置提供者（必填）。 */
        public Builder configProvider(InvoiceConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** 时钟（测试注入固定时钟用）。 */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** 版式文件固化选项（下载/落盘行为）。 */
        public Builder fileOptions(io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions fileOptions) {
            this.fileOptions = fileOptions;
            return this;
        }

        /** 构建；渠道配置提供者必填。 */
        public InvoiceGateway build() {
            if (configProvider == null) {
                throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "未设置渠道配置提供者");
            }
            return new InvoiceGateway(this);
        }
    }
}
