package io.github.lianghang1.conflux.invoice.channels.piaotong;

import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 票通云渠道配置参数（从 {@code conflux.invoice.*} 配置解析）。
 *
 * <p>必填凭据（均由票通分配）：</p>
 * <ul>
 *   <li>{@code platformCode} 第三方平台编码（8 位）；</li>
 *   <li>{@code aliasName} 平台简称（4 位，用于拼 20 位流水号）；</li>
 *   <li>{@code password} 3DES 密钥（请求加密与响应解密共用）；</li>
 *   <li>{@code privateKey} 我方 RSA 私钥（PKCS8，请求签名用）；</li>
 *   <li>{@code publicKey} 票通平台 RSA 公钥（X509，响应与回调验签用）；</li>
 *   <li>{@code sellerTaxNo} 销方税号（报文 {@code taxpayerNum}）；</li>
 *   <li>{@code account} 电子税局登录账号（登录认证链路使用，可空）。</li>
 * </ul>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PiaoTongParams extends ChannelConfig {

    /** 第三方平台编码（8 位，票通分配）。 */
    private String platformCode;
    /** 平台简称（4 位，拼 {@code invoiceReqSerialNo}/{@code serialNo}）。 */
    private String aliasName;
    /** 3DES 密钥（ECB/UTF-8）。 */
    private String password;
    /** 我方 RSA 私钥（PKCS8 Base64，请求签名）。 */
    private String privateKey;
    /** 票通平台 RSA 公钥（X509 Base64，响应/回调验签）。 */
    private String publicKey;
    /** 电子税局登录账号（手机号/身份证号，认证链路）。 */
    private String account;
    /** 销方名称（部分报文可带）。 */
    private String sellerName;
    /** 服务地址，默认 {@link PiaoTongSpec#DEFAULT_BASE_URL}。 */
    private String baseUrl;
    /** HTTP 超时毫秒。 */
    private long timeoutMillis = 30000L;
    /** 渠道侧应用标识（多销方路由用）。 */
    private String channelAppIdValue;

    public String channelAppId() {
        return channelAppIdValue;
    }

    public String sellerTaxNo() {
        return getSellerTaxNo();
    }

    public String url(String path) {
        String base = baseUrl == null || baseUrl.trim().isEmpty()
                ? PiaoTongSpec.DEFAULT_BASE_URL : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /** 凭据是否齐备（不抛异常，供可降级的辅助查询使用）。 */
    public boolean hasCredentials() {
        return isNotBlank(platformCode) && isNotBlank(aliasName)
                && isNotBlank(password) && isNotBlank(privateKey)
                && isNotBlank(publicKey) && isNotBlank(getSellerTaxNo());
    }

    private static boolean isNotBlank(String v) {
        return v != null && !v.trim().isEmpty();
    }
}
