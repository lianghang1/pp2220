package io.github.lianghang1.conflux.invoice.model;

/**
 * 发票通道分级：不同接入方式的能力、稳定性与时效差异极大。
 *
 * <p>支付渠道之间没有这种分层，但数电发票同时存在「乐企直连税局」
 * 「服务商开放 API」「电局网页/模拟登录」三类通道，同一家企业可能多级并存，
 * 路由时需按 {@link #getPriority()} 择优，失败后降级。</p>
 *
 * @author lianghang
 */
public enum ChannelTier {

    /** 乐企/税局直连：能力最完整、最稳定，但需要企业准入与税局备案。 */
    DIRECT_TAX(300, "乐企/税局直连"),
    /** 服务商开放 API：公开文档、可自动化，能力受平台封装限制。 */
    SERVICE_API(200, "服务商开放 API"),
    /** 电局网页/模拟 RPA：依赖登录态、扫脸、短信，最不稳定，作为兜底。 */
    WEB_PORTAL(100, "电局网页/模拟 RPA");

    private final int priority;
    private final String label;

    ChannelTier(int priority, String label) {
        this.priority = priority;
        this.label = label;
    }

    /** 数值越大优先级越高。 */
    public int getPriority() {
        return priority;
    }

    public String getLabel() {
        return label;
    }
}
