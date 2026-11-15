package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;

/**
 * 发票修复能力：从局端补拉已开具发票（渠道侧补偿动作）。
 *
 * <p>可选能力，{@code InvoiceChannelProvider.repairer()} 返回 null 表示渠道不支持。
 * 申请与结果查询分离（多数渠道修复是异步、不支持结果回调），且<b>不改单据状态机</b>。</p>
 *
 * @author lianghang
 */
public interface InvoiceRepairer {

    /** 提交修复申请。 */
    RepairResult apply(InvoiceContext ctx, RepairRequest request);

    /** 查询修复结果；使用与申请相同的 channelOrderNo。 */
    RepairResult query(InvoiceContext ctx, RepairRequest request);
}
