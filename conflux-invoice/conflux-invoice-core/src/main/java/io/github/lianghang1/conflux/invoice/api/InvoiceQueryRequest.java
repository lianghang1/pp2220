package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 发票查单请求：可凭网关单据号、业务单号、渠道流水号或<b>票号</b>定位。
 *
 * <p>查单不只用于前端查询，也是补单/轮询的统一入口：异步开票、授权挂起、
 * 红字确认单等待确认都靠它推进状态。按票号定位用于对账 / 换渠道补单场景，
 * 复用 {@code InvoiceStore.findByInvoiceNo(invoiceNo, invoiceCode)}。</p>
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
    /** 发票号码 / 数电发票号码（与 invoiceCode 配合按票号定位）。 */
    private String invoiceNo;
    /** 发票代码（税控票必填，数电票可空）。 */
    private String invoiceCode;
}
