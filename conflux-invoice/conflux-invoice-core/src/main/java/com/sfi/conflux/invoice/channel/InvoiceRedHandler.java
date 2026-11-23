package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.api.QuickRedOutcome;
import com.sfi.conflux.invoice.api.RedApplyResult;
import com.sfi.conflux.invoice.api.RedConfirmRequest;

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

    /**
     * 快捷红冲：渠道一次调用完成红冲，返回红票受理结果或待确认状态。
     */
    QuickRedOutcome quickRed(InvoiceContext ctx);

    /**
     * 主动确认或拒绝红字确认单（与 {@link #queryRed} 的「查状态」分开）。
     *
     * <p><b>可选能力</b>：默认返回 null，编排层据此抛
     * {@code CHANNEL_CAPABILITY_NOT_SUPPORTED}，由使用方决定改走查询状态流程。</p>
     */
    default RedApplyResult confirm(InvoiceContext ctx, RedConfirmRequest request) {
        return null;
    }
}
