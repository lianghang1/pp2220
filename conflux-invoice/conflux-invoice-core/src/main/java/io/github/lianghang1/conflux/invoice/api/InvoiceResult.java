package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 发票操作统一结果。
 *
 * <p>渠道能力实现用静态工厂表达「受理/等待授权/成功/失败」，编排器用
 * {@link #fromInvoice(Invoice, List, AuthChallenge)} 把持久化实体转为对使用方的返回，
 * 保证同一份状态、同一套字段，不出现两套模型漂移。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String invoiceId;
    private String bizOrderNo;
    private InvoiceState state;
    private String channelCode;
    private String channelInstanceId;
    private String channelOrderNo;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private LocalDateTime invoiceTime;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    /** 已归档的版式文件（含法定 XML 原件）。 */
    private List<InvoiceFile> files;
    /** WAITING_AUTH 时非空的授权挑战。 */
    private AuthChallenge authChallenge;
    /** 渠道是否已转异步受理。 */
    private boolean asyncAccepted;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文（排障用）。 */
    private String rawPayload;
    /**
     * 使用方保管的发票载体（含完整票面与渠道要素）。
     *
     * <p>SDK 无台账——开票/查单结果把完整载体返回给使用方，后续红冲、交付、
     * 修复时随请求原样回传。使用方据此更新自己的单据与业务状态。</p>
     */
    private Invoice invoice;
    /**
     * 授权会话载体（WAITING_AUTH 时非空，使用方回传至 AuthSubmission）。
     *
     * <p>SDK 无台账——授权上下文随结果返回、随提交回传，SDK 不暂存会话。</p>
     */
    private AuthSession authSession;

    // ---- 渠道侧工厂 ----

    public static InvoiceResult accepted(String channelOrderNo, boolean asyncAccepted) {
        return new InvoiceResult().setState(InvoiceState.PROCESSING)
                .setChannelOrderNo(channelOrderNo).setAsyncAccepted(asyncAccepted);
    }

    public static InvoiceResult waitingAuth(AuthChallenge challenge) {
        return new InvoiceResult().setState(InvoiceState.WAITING_AUTH)
                .setAuthChallenge(challenge);
    }

    public static InvoiceResult succeeded(String channelOrderNo, String invoiceNo,
                                          String invoiceCode, LocalDate invoiceDate,
                                          List<InvoiceFile> files) {
        return new InvoiceResult().setState(InvoiceState.SUCCEEDED)
                .setChannelOrderNo(channelOrderNo).setInvoiceNo(invoiceNo)
                .setInvoiceCode(invoiceCode).setInvoiceDate(invoiceDate)
                .setFiles(files == null ? Collections.<InvoiceFile>emptyList() : files);
    }

    public static InvoiceResult failed(String errorCode, String errorMsg) {
        return new InvoiceResult().setState(InvoiceState.FAILED)
                .setErrorCode(errorCode).setErrorMsg(errorMsg);
    }

    // ---- 编排器出口 ----

    /** 由持久化实体构造对外结果。 */
    public static InvoiceResult fromInvoice(Invoice invoice, List<InvoiceFile> files,
                                            AuthChallenge challenge) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceResult()
                .setInvoiceId(invoice.getInvoiceId())
                .setBizOrderNo(invoice.getBizOrderNo())
                .setState(invoice.getState())
                .setChannelCode(invoice.getChannelCode())
                .setChannelInstanceId(invoice.getChannelInstanceId())
                .setChannelOrderNo(invoice.getChannelOrderNo())
                .setInvoiceNo(invoice.getInvoiceNo())
                .setInvoiceCode(invoice.getInvoiceCode())
                .setInvoiceDate(invoice.getInvoiceDate())
                .setInvoiceTime(invoice.getInvoiceTime())
                .setAmount(invoice.getAmount())
                .setTaxAmount(invoice.getTaxAmount())
                .setFiles(files == null ? Collections.<InvoiceFile>emptyList() : files)
                .setAuthChallenge(challenge)
                .setAsyncAccepted(invoice.getState() == InvoiceState.PROCESSING)
                .setErrorCode(invoice.getErrorCode())
                .setErrorMsg(invoice.getErrorMsg())
                .setInvoice(invoice);
    }

    /** 是否携带了法定 XML 原件。 */
    public boolean hasLegalXml() {
        if (files == null) {
            return false;
        }
        for (InvoiceFile file : files) {
            if (file != null && file.getType() == InvoiceFileType.XML && file.hasPayload()) {
                return true;
            }
        }
        return false;
    }
}
