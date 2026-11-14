package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.api.InvoiceResult;

/**
 * 蓝字发票开具能力。
 *
 * <p>返回值必须显式表达四种结果之一：{@code PROCESSING}（异步受理）、
 * {@code WAITING_AUTH}（需人工授权）、{@code SUCCEEDED}（同步完成）、
 * {@code FAILED}（失败）。渠道不得返回「无状态」的成功。</p>
 *
 * @author lianghang
 */
public interface InvoiceIssuer {

    InvoiceResult issue(InvoiceContext ctx);
}
