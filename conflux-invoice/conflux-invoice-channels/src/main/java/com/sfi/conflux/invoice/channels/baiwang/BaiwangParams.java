package com.sfi.conflux.invoice.channels.baiwang;

import com.sfi.conflux.invoice.channel.ChannelConfig;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 百望云（BOPSDK）渠道配置。
 *
 * <p>对应开放平台公共参数：{@code appKey}/{@code appSecret} 用于签名，
 * {@code serverUrl} 为网关地址（沙箱/生产），{@code salt} 为可选的盐值
 * （部分平台版本签名时叠加）。SDK 内部按 appSecret + 时间戳计算 token，
 * 也允许用 {@code accessToken} 显式覆盖（走登录接口获取的场景）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class BaiwangParams extends ChannelConfig {

    /** 渠道应用标识（多销方税号路由用）。 */
    private String channelAppId;
    /** 销方名称。 */
    private String sellerName;
    /** 开放平台 appKey。 */
    private String appKey;
    /** 开放平台 appSecret（签名密钥）。 */
    private String appSecret;
    /** 签名盐值（可选，部分平台版本需要）。 */
    private String salt;
    /** 网关地址，如 https://openapi.baiwang.com/router/rest。 */
    private String serverUrl;
    /** 显式 token（配置后跳过登录直接复用；为空则按 username/password 登录获取）。 */
    private String accessToken;
    /** 登录用户名（租户管理员账号，用于 PasswordLoginClient 获取 token）。 */
    private String username;
    /** 登录密码。 */
    private String password;
    /** 登录盐值（userSalt）。 */
    private String userSalt;
    /** 开票终端/数电账号（invoiceTerminalCode）。 */
    private String invoiceTerminalCode;
    /** 设备类型（deviceType，如 0）。 */
    private String deviceType;
    /** 默认开票员/收款人/复核人。 */
    private String drawer;
    private String payee;
    private String checker;
    /** 销方地址、电话、开户行、账号（文档开票请求拆分字段）。 */
    private String sellerAddress;
    private String sellerPhone;
    private String sellerBankName;
    private String sellerBankNumber;
    /** 渠道回调地址（callBackUrl）。 */
    private String callbackUrl;
    /** 调用超时（毫秒）。 */
    private long timeoutMillis = 30000L;

    public String channelAppId() {
        return channelAppId;
    }

    public String sellerTaxNo() {
        return getSellerTaxNo();
    }

    /** 网关凭据是否齐备（开票/查询主链路要求）。 */
    public boolean hasCredentials() {
        return isNotBlank(appKey) && isNotBlank(appSecret) && isNotBlank(serverUrl);
    }

    /** 是否具备登录取 token 的条件（有显式 token，或用户名密码齐全）。 */
    public boolean hasTokenSource() {
        return isNotBlank(accessToken) || (isNotBlank(username) && isNotBlank(password));
    }

    private static boolean isNotBlank(String value) {
        return value != null && value.trim().length() > 0;
    }
}
