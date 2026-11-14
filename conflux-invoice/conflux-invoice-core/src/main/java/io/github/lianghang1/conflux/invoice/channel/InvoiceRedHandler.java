package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;

/**
 * 红字能力：两段式红冲，与支付退款「一次调用」的语义完全不同。
 *
 * <ol>
 *   <li>{@link #apply} 申请红字确认单 → 返回 WAITING_CONFIRM 与确认截止时间；</li>
 *   <li>{@link #queryRed} 轮询/由回调驱动 → CONFIRMED / REJECTED / EXPIRED；</li>
 *   <li>{@link #issueRed} 仅在 CONFIRMED 后调用 → 返回红字发票开具结果。</li>
 * </ol>
 *
 * @author lianghang
 */
public interface InvoiceRedHandler {

    /** 申请红字确认单（第一段）。 */
    RedApplyResult apply(InvoiceContext ctx);

    /** 查询红字确认单状态（第二段之前的确认环节）。 */
    RedApplyResult queryRed(InvoiceContext ctx);

    /** 确认后开具红字发票（第二段）。 */
    InvoiceResult issueRed(InvoiceContext ctx);
}
