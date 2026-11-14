package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 红字发票开具请求（两段红冲的第二段）。
 *
 * <p>仅当红字确认单状态为 {@code CONFIRMED} 时允许调用，否则抛
 * {@code InvoiceErrorCode.RED_CONFIRM_PENDING}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    /** 红字确认单号或业务单号（二选一）。 */
    private String redApplyId;
    private String bizRedNo;
    private String channelCode;
    private String notifyUrl;
}
