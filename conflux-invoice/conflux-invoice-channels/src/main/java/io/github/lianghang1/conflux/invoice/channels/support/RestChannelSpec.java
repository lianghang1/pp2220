package io.github.lianghang1.conflux.invoice.channels.support;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

/**
 * REST 渠道规格：把「渠道编码 / 通道级别 / 接口路径 / 关键字段」集中描述，
 * 让同一份通用 REST 适配器承载多家服务商，渠道差异只留在规格里。
 *
 * <p><b>文档核对标记</b>：{@link #documentVerified} 为 false 表示该平台的接口路径与
 * 字段映射尚未按其公开文档逐项核对，此时所有路径默认为空、对应能力不会暴露，
 * 使用方必须自行配置路径与字段名后方可启用。框架不编造未经核实的 URL 与字段。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RestChannelSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private String displayName;
    private ChannelTier tier = ChannelTier.SERVICE_API;
    private Set<InvoiceType> supportedTypes = EnumSet.of(
            InvoiceType.DIGITAL_VAT_SPECIAL, InvoiceType.DIGITAL_VAT_GENERAL);
    /** 默认网关地址（可被配置覆盖）。 */
    private String baseUrl;
    /** 接口路径与字段是否已按平台公开文档核对。 */
    private boolean documentVerified;
    /** 文档核对说明/待办。 */
    private String docNote;

    // ---- 能力路径（为空表示该能力不暴露） ----
    private String issuePath;
    private String queryPath;
    private String filePath;
    private String redApplyPath;
    private String redQueryPath;
    private String redIssuePath;
    private String authStartPath;
    private String authSubmitPath;

    /** 开票 action 编码（如企享云蓝字开具 fpkj_zzs）。 */
    private String actionCode;
    /** 渠道表示「转异步」的响应码（如企享云 2001）。 */
    private String asyncCode;
    /** 渠道表示成功的响应码集合。 */
    private String[] successCodes = {"0", "200", "0000", "SUCCESS"};

    /**
     * 是否为「配置驱动骨架」：文档未逐项核对的渠道不预设任何路径，
     * 但允许使用方按平台公开文档在 attrs 中配置路径后启用全部能力。
     */
    public boolean isConfigDriven() {
        return !documentVerified;
    }

    public boolean hasIssue() {
        return issuePath != null && issuePath.length() > 0;
    }

    public boolean hasRed() {
        return notBlank(redApplyPath) && notBlank(redQueryPath) && notBlank(redIssuePath);
    }

    public boolean hasAuth() {
        return notBlank(authStartPath) && notBlank(authSubmitPath);
    }

    private static boolean notBlank(String value) {
        return value != null && value.trim().length() > 0;
    }
}
