package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 红字确认单申请请求（两段红冲的第一段）。
 *
 * <p>原票可凭 {@code originalInvoiceId}（网关单据号）或
 * {@code originalInvoiceNo + originalInvoiceCode}（票号）定位。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedApplyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    /** 使用方业务单号（幂等键，必填）。 */
    private String bizRedNo;
    private String originalInvoiceId;
    private String originalInvoiceNo;
    private String originalInvoiceCode;
    /** 红冲原因（必填）。 */
    private String reason;
    /** 红冲金额（元，为空表示全额）。 */
    private BigDecimal amount;
    /** 指定渠道（为空沿用原票渠道）。 */
    private String channelCode;
    private String notifyUrl;
}
