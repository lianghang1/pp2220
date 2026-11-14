package io.github.lianghang1.conflux.invoice.channels.support;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;

/**
 * 四家数电票服务商的渠道规格。
 *
 * <p><b>文档状态如实标注</b>：目前只有企享云「蓝字发票开具」接口的公开字段
 * 被逐项核对过；百旺金穗云公开文档页抓到的是「进项发票入账提交接口」
 * （{@code /swgx-saas/swgx-api/interface/jxgl/creditSubmit}），并非蓝字开具接口，
 * 不能拿来当开票用。百旺票税云、金蝶发票云的接口路径与字段尚未逐项核对。
 * 因此后三家的规格默认不带任何路径，注册后不会暴露「假装能用」的渠道能力；
 * 使用方按平台最新公开文档在 {@code attrs} 中配置路径与字段映射后即可启用。</p>
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

    /** 百旺金穗云：待核对蓝字开具/红字/文件接口文档页。 */
    public static RestChannelSpec baiwangjs() {
        return new RestChannelSpec()
                .setChannelCode("baiwangjs")
                .setDisplayName("百旺金穗云")
                .setBaseUrl("https://api.baiwangjs.com")
                .setDocumentVerified(false)
                .setDocNote("公开文档页当前抓到的是「进项发票入账提交接口」"
                        + "(/swgx-saas/swgx-api/interface/jxgl/creditSubmit)，并非蓝字开具接口。"
                        + "启用前需重新定位开具/红冲/查询/文件下载接口文档，"
                        + "并在 attrs 中配置 issuePath / queryPath / filePath 等路径与字段映射。");
    }

    /** 百旺票税云：待核对公开文档字段。 */
    public static RestChannelSpec baiwang() {
        return new RestChannelSpec()
                .setChannelCode("baiwang")
                .setDisplayName("百旺票税云")
                .setDocumentVerified(false)
                .setDocNote("待按平台最新公开 API 文档核对接口路径、票种编码与签名规则，"
                        + "并在 attrs 中配置 baseUrl / issuePath / queryPath / filePath 等后启用。");
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
