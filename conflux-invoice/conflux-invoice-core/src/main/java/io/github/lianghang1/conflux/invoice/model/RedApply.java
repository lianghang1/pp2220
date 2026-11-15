package io.github.lianghang1.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 红字确认单实体：两段红冲的第一段。
 *
 * <p>与支付退款单不同，红字确认单需要对方（购方）在 {@link #confirmDeadline}
 * 之前确认，期间单据挂起；确认后才生成 {@link #redInvoiceId} 对应的红字发票。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedApply implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关红字确认单号（主键）。 */
    private String redApplyId;
    private String appId;
    /** 使用方业务单号（幂等键）。 */
    private String bizRedNo;
    /** 原蓝字发票单据号。 */
    private String originalInvoiceId;
    private String originalInvoiceNo;
    private String originalInvoiceCode;
    /** 红冲原因。 */
    private String reason;
    /** 冲红原因代码：1 开票有误（全额红冲）、2 销货退回、3 服务终止、4 销售折让。 */
    private String chyydm;
    /** 红冲金额（元）。 */
    private BigDecimal amount;
    private String channelCode;
    private String channelInstanceId;
    private String accountKey;
    /** 渠道侧申请流水号。 */
    private String channelOrderNo;
    /** 红字确认单编号（渠道/税局回传）。 */
    private String redConfirmNo;
    private RedApplyState state;
    /** 对方确认截止时间。 */
    private LocalDateTime confirmDeadline;
    private LocalDateTime confirmedTime;
    /** 确认后开具的红字发票单据号。 */
    private String redInvoiceId;
    private String notifyUrl;
    private String errorCode;
    private String errorMsg;

    /** 乐观锁版本号。 */
    private long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
