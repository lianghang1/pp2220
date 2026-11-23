package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.invoice.api.InvoiceResult;

/**
 * 查单/结果轮询能力：异步开票、授权挂起、补单扫描的统一入口。
 *
 * @author lianghang
 */
public interface InvoiceQuerier {

    InvoiceResult query(InvoiceContext ctx);
}
