package io.github.lianghang1.conflux.invoice.channels.support;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;

/**
 * 通用 REST 适配器的渠道规格。
 *
 * <p>当前仅企享云复用通用 REST 适配器，其「蓝字发票开具」接口的公开字段已逐项核对。
 * 其余渠道（百旺金穗云、百望云、票通云、诺诺网）协议特殊，均由各自的专属适配器实现
 * （{@code BaiwangJsChannelProvider} / {@code BaiwangChannelProvider} /
 * {@code PiaoTongChannelProvider} / {@code NuonuoChannelProvider}），不走本通用 REST 适配器；
 * 详见各自的 {@code *Spec} 与 {@code docs/invoice-platforms.md}。</p>
 *
 * @author lianghang
 */
public final class RestSpecs {

    private RestSpecs() {
    }

    /** 企享云：蓝字开具接口及字段来自其公开 API 文档示例。 */
    public static RestChannelSpec qixiangyun() {
        return new RestChannelSpec()
                .setChannelCode("qixiangyun")
                .setDisplayName("企享云")
                .setTier(ChannelTier.SERVICE_API)
                .setBaseUrl("https://api.qixiangyun.com")
                .setDocumentVerified(true)
                .setDocNote("已核对公开文档「蓝字发票开具」：POST /v2/invoice/qdfp/fpkjZzs，"
                        + "字段 action/async/nsrsbh/ly_ddbh/wjgs/fplxdm/xmmx/jshj/hjse/data.xsf_yhmc；"
                        + "响应 reqId/code/success/message/data.fphm，code=2001 表示转异步。"
                        + "查单、红字、授权、文件下载接口需按平台文档另行配置路径。")
                .setIssuePath("/v2/invoice/qdfp/fpkjZzs")
                .setActionCode("fpkj_zzs")
                .setAsyncCode("2001");
    }
}
