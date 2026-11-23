package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.invoice.api.DeliveryRequest;
import com.sfi.conflux.invoice.api.DeliveryResult;

/**
 * 发票交付能力：短信 / 邮箱重新交付发票版式信息。
 *
 * <p>可选能力，{@code InvoiceChannelProvider.deliverer()} 返回 null 表示渠道不支持，
 * 注册表统一抛 {@code CHANNEL_CAPABILITY_NOT_SUPPORTED}。交付不改变单据状态。</p>
 *
 * @author lianghang
 */
public interface InvoiceDeliverer {

    /** 重新交付；返回渠道受理结果。 */
    DeliveryResult resend(InvoiceContext ctx, DeliveryRequest request);
}
