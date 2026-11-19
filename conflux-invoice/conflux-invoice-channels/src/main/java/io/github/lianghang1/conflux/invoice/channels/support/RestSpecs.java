package io.github.lianghang1.conflux.invoice.channels.support;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;

/**
 * 数电票服务商的渠道规格（供通用 REST 适配器使用）。
 *
 * <p><b>文档状态如实标注</b>：企享云「蓝字发票开具」接口的公开字段已逐项核对。
 * 百旺金穗云、百望云、票通云、诺诺网协议特殊，均由各自的专属适配器实现
 * （{@code BaiwangJsChannelProvider} / {@code BaiwangChannelProvider} /
 * {@code PiaoTongChannelProvider} / {@code NuonuoChannelProvider}），不走本通用 REST 适配器；
 * 详见各自的 {@code *Spec} 与 {@code docs/invoice-platforms.md}。
 * 本类中对应的 {@code baiwangjs()} / {@code baiwang()} / {@code piaotong()} 仅作元数据占位。
 * 金蝶发票云的接口路径与字段尚未逐项核对，规格默认不带任何路径，
 * 注册后不会暴露「假装能用」的渠道能力。</p>
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

    /**
     * 百旺金穗云：已按官方公开文档核对，但<b>不由通用 REST 适配器承载</b>。
     *
     * <p>销项开票、红冲、登录、认证、回调均有完整公开文档。由于本渠道是
     * Base64 报文 + HMAC 网关签名 + 全异步回调，与通用适配器的「明文 JSON +
     * 同步响应」假设不兼容，改由 {@link io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsChannelProvider}
     * 独立实现。本规格仅作元数据占位，documentVerified 置 true 表示文档已核对。</p>
     */
    public static RestChannelSpec baiwangjs() {
        return new RestChannelSpec()
                .setChannelCode("baiwangjs")
                .setDisplayName("百旺金穗云")
                .setBaseUrl("https://api.baiwangjs.com")
                .setDocumentVerified(true)
                .setDocNote("已按官方公开文档核对；由专属适配器 BaiwangJsChannelProvider 实现，"
                        + "支持 V2 优先 / V1 可切换，接口路径见 BaiwangJsSpec。");
    }

    /**
     * 票通云：已按《票通数电发票接口文档 3.2.0》核对，不由通用 REST 适配器承载。
     *
     * <p>协议为「明文 JSON 信封 + 3DES 业务报文 + RSA SHA1withRSA 字典序签名 +
     * 响应验签解密」，与通用适配器假设不兼容，改由
     * {@link io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongChannelProvider}
     * 独立实现。本规格仅作元数据占位。</p>
     */
    public static RestChannelSpec piaotong() {
        return new RestChannelSpec()
                .setChannelCode("piaotong")
                .setDisplayName("票通云")
                .setBaseUrl("https://fpkj.vpiaotong.com")
                .setDocumentVerified(true)
                .setDocNote("已按票通数电发票接口文档 3.2.0 核对；由专属适配器 "
                        + "PiaoTongChannelProvider 实现，接口路径见 PiaoTongSpec。");
    }

    /**
     * 百望云：文档已核对，由专属适配器 {@code BaiwangChannelProvider}（官方 BOPSDK）实现，
     * 不走本通用 REST 适配器。本规格仅作元数据占位，故 documentVerified 保持 false
     * （通用规格未配置路径），实际能力全部在 {@code BaiwangSpec} / 专属适配器内。
     */
    public static RestChannelSpec baiwang() {
        return new RestChannelSpec()
                .setChannelCode("baiwang")
                .setDisplayName("百望云")
                .setDocumentVerified(false)
                .setDocNote("百望云由专属适配器 BaiwangChannelProvider 实现，能力见 BaiwangSpec "
                        + "与 docs/invoice-platforms.md 第 3.5 节；本通用规格无实际路径。");
    }

    /** 金蝶发票云：待核对公开文档字段。 */
    public static RestChannelSpec kingdee() {
        return new RestChannelSpec()
                .setChannelCode("kingdee")
                .setDisplayName("金蝶发票云")
                .setDocumentVerified(false)
                .setDocNote("待按平台最新公开 API 文档核对接口路径、票种编码与签名规则，"
                        + "并在 attrs 中配置 baseUrl / issuePath / queryPath / filePath 等后启用。");
    }
}
