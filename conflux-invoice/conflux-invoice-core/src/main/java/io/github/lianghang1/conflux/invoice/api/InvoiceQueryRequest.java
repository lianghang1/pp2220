package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 发票查单请求：可凭网关单据号、业务单号或渠道流水号定位。
 *
 * <p>查单不只用于前端查询，也是补单/轮询的统一入口：异步开票、授权挂起、
 * 红字确认单等待确认都靠它推进状态。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    private String invoiceId;
    private String bizOrderNo;
    private String channelCode;
    private String channelOrderNo;
}
