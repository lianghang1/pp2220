package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 百旺金穗云渠道参数：从 {@code conflux.invoice.configs.<configId>.baiwangjs.attr} 解析。
 *
 * <p><b>凭据</b>：网关 KEY / SECRET 用于 HMAC-SHA256 签名（非业务 appKey/appSecret）；
 * {@code qyId} 为企业唯一标识，通过 3.1.1 接口查询一次即可，也可直接配置；
 * {@code dlzh} 是开票员的电子税务局登录账号（一般为手机号）。</p>
 *
 * <p><b>版本</b>：{@link #apiVersion} 默认 {@link BaiwangJsApiVersion#V2}；
 * 置为 V1 时开票/查单走 V1 路径并使用 V1 合并字段报文。所有路径均允许 attr 覆盖。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class BaiwangJsParams extends ChannelConfig {

    /** 网关 KEY（Authorization 的 username）。 */
    private String gatewayKey;
    /** 网关 SECRET（HMAC-SHA256 密钥）。 */
    private String gatewaySecret;
    /** 企业唯一标识（3.1.1 查询获得，必需）。 */
    private String qyId;
    /** 电子税务局登录账号（开票员手机号，数电票必填）。 */
    private String dlzh;
    /** 开票人。 */
    private String kpr;
    /** 申请人。 */
    private String sqr;
    /** 销方名称（缺省取单据 sellerName）。 */
    private String sellerName;
    private String sellerAddress;
    private String sellerPhone;
    private String sellerBank;
    private String sellerBankAccount;

    /** 渠道侧应用标识：网关 KEY 优先，回落 qyId。 */
    private String channelAppId;

    private String baseUrl = BaiwangJsSpec.DEFAULT_BASE_URL;
    private String host = BaiwangJsSpec.DEFAULT_HOST;
    private String userAgent = BaiwangJsSpec.DEFAULT_UA;
    private long timeoutMillis = 30000L;

    /** 接口版本：V2 优先，V1 保留实现。 */
    private BaiwangJsApiVersion apiVersion = BaiwangJsApiVersion.V2;

    /** 票种 → fplxdm 编码映射（键为 InvoiceType.name()）。 */
    private Map<String, String> invoiceTypeCodes = new LinkedHashMap<String, String>();

    public String channelAppId() {
        return channelAppId;
    }

    public String sellerTaxNo() {
        return getSellerTaxNo();
    }

    /** 是否使用 V1 报文与路径。 */
    public boolean isV1() {
        return apiVersion == BaiwangJsApiVersion.V1;
    }

    /** 取能力路径：attr 覆盖优先于版本默认值。 */
    public String path(String key, String v2Path, String v1Path) {
        Map<String, String> rawAttr = getAttr();
        String override = rawAttr == null ? null : rawAttr.get(key);
        if (override != null && override.trim().length() > 0) {
            return override.trim();
        }
        return isV1() && v1Path != null ? v1Path : v2Path;
    }

    /** 拼接完整 URL。 */
    public String url(String path) {
        if (path == null || path.length() == 0) {
            throw new IllegalArgumentException("百旺金穗云接口路径未配置");
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = baseUrl == null ? "" : baseUrl;
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    /** 签名用 request-line：完整 URL 的 path 部分（含网关前缀）。 */
    public String requestLine(String fullUrl) {
        int scheme = fullUrl.indexOf("://");
        int start = scheme < 0 ? -1 : fullUrl.indexOf('/', scheme + 3);
        if (start < 0) {
            return "/";
        }
        int query = fullUrl.indexOf('?', start);
        int end = query < 0 ? fullUrl.length() : query;
        return fullUrl.substring(start, end);
    }

    /** 票种编码：attr 的 {@code fplxdm.<InvoiceType>} 优先，其次文档默认映射。 */
    public String invoiceTypeCode(InvoiceType type) {
        if (type == null) {
            return null;
        }
        String configured = invoiceTypeCodes == null ? null : invoiceTypeCodes.get(type.name());
        if (configured != null && configured.length() > 0) {
            return configured;
        }
        switch (type) {
            case DIGITAL_VAT_SPECIAL:
                return BaiwangJsSpec.FPLXDM_DIGITAL_SPECIAL;
            case DIGITAL_VAT_GENERAL:
                return BaiwangJsSpec.FPLXDM_DIGITAL_GENERAL;
            case VAT_SPECIAL:
                return BaiwangJsSpec.FPLXDM_VAT_SPECIAL;
            case VAT_GENERAL:
                return BaiwangJsSpec.FPLXDM_VAT_GENERAL;
            case VEHICLE_SALES:
                return BaiwangJsSpec.FPLXDM_VEHICLE;
            default:
                return type.name();
        }
    }
}
