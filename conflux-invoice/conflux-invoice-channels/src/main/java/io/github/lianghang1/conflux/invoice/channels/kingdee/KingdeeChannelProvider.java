package io.github.lianghang1.conflux.invoice.channels.kingdee;

import io.github.lianghang1.conflux.invoice.channels.support.AbstractRestInvoiceChannel;
import io.github.lianghang1.conflux.invoice.channels.support.RestSpecs;

/**
 * kingdee 渠道实现。
 *
 * <p>继承通用 REST 适配器，渠道差异全部集中在 {@code RestSpecs.kingdee()} 规格里；
 * 规格中的 {@code documentVerified} 标记了接口路径与字段是否已按平台公开文档核对，
 * 未核对的渠道需在使用方配置中补齐路径后方可启用相应能力。</p>
 *
 * @author lianghang
 */
public class KingdeeChannelProvider extends AbstractRestInvoiceChannel {

    public KingdeeChannelProvider() {
        super(RestSpecs.kingdee());
    }
}
