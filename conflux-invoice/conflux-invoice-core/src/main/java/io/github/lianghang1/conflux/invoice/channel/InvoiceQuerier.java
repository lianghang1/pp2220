package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.api.InvoiceResult;

/**
 * 查单/结果轮询能力：异步开票、授权挂起、补单扫描的统一入口。
 *
 * @author lianghang
 */
public interface InvoiceQuerier {

    InvoiceResult query(InvoiceContext ctx);
}
