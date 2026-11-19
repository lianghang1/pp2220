package io.github.lianghang1.conflux.invoice.channels.nuonuo;

import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诺诺渠道参数：从 {@code conflux.invoice.apps.<appId>.nuonuo.attrs} 解析。
 *
 * <p><b>凭据</b>：{@code appKey}/{@code appSecret} 用于官方 SDK 签名与令牌获取；
 * {@code accessToken} 为业务接口令牌——沙箱环境允许为空，正式环境需配置，
 * 或配置后由网关按 getMerchantToken 返回解析。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NuonuoParams implements InvoiceChannelParams, Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台分配的应用 appKey。 */
    private String appKey;
    /** 平台分配的应用 appSecret（仅用于签名/令牌，不落日志）。 */
    private String appSecret;
    /** 业务接口令牌（沙箱可为空；正式环境必填或由 token 接口获取）。 */
    private String accessToken;
    /** 销方税号（自用型应用开票时作为 salerTaxNum）。 */
    private String sellerTaxNo;
    /** 销方名称。 */
    private String sellerName;
    /** 开票员（clerk）。 */
    private String drawer;
    /** 销方电话。 */
    private String sellerPhone;
    /** 销方地址。 */
    private String sellerAddress;
    /** 销方银行账号。 */
    private String sellerBankAccount;
    /** 回调地址（可覆盖单据 notifyUrl）。 */
    private String callbackUrl;

    /** 网关地址（默认按 sandbox 选正式/沙箱）。 */
    private String gateway = NuonuoSpec.DEFAULT_GATEWAY;
    /** 是否沙箱。 */
    private boolean sandbox;
    /** 调用超时（毫秒），传给 SDK 的可变参。 */
    private int timeoutMillis = 30000;

    /**
     * 从 getMerchantToken 返回 JSON 中提取令牌时，令牌值的字段路径（点分，如
     * {@code result.accessToken}）。为空时网关按常见字段自动尝试，不臆造固定结构。
     */
    private String tokenJsonPath;

    /** 渠道侧应用标识：appKey 优先，回落 sellerTaxNo。 */
    private String channelAppId;

    /** 原始配置。 */
    private Map<String, String> attrs = new LinkedHashMap<String, String>();

    @Override
    public String channelAppId() {
        return channelAppId;
    }

    @Override
    public String sellerTaxNo() {
        return sellerTaxNo;
    }

    public boolean hasCredentials() {
        return isNotBlank(appKey) && isNotBlank(appSecret) && isNotBlank(gateway);
    }

    /** 是否需要令牌：沙箱允许空 token。 */
    public boolean requiresToken() {
        return !sandbox;
    }

    private static boolean isNotBlank(String v) {
        return v != null && v.trim().length() > 0;
    }
}
