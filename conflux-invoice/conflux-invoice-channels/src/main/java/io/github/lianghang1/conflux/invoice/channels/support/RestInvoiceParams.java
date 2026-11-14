package io.github.lianghang1.conflux.invoice.channels.support;

import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用 REST 渠道参数：从配置 {@code attrs} 解析。
 *
 * <p>所有接口路径都允许按配置覆盖，这是「文档未核对的渠道先按骨架注册、
 * 待核对后再启用」的实现基础。渠道专属签名、证书等可按平台需要扩展本类。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RestInvoiceParams implements InvoiceChannelParams, Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧应用标识（appKey / 企业编码），也是账户键的第一段。 */
    private String channelAppId;
    private String sellerTaxNo;
    private String appKey;
    private String appSecret;
    private String baseUrl;
    private long timeoutMillis = 30000L;
    private boolean sandbox;
    /** 原始配置，路径覆盖从这里读取。 */
    private Map<String, String> attrs = new HashMap<String, String>();
    /** 票种 → 渠道 fplxdm 编码映射（键为 InvoiceType.name()）。 */
    private Map<String, String> invoiceTypeCodes = new HashMap<String, String>();

    @Override
    public String channelAppId() {
        return channelAppId;
    }

    @Override
    public String sellerTaxNo() {
        return sellerTaxNo;
    }

    /** 取能力路径：配置覆盖优先于规格默认值。 */
    public String path(String key, String specValue) {
        String override = attrs == null ? null : attrs.get(key);
        return override != null && override.trim().length() > 0 ? override.trim() : specValue;
    }

    /** 拼接完整 URL。 */
    public String url(String path) {
        if (path == null || path.length() == 0) {
            throw new IllegalArgumentException("渠道接口路径未配置");
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

    /** 票种编码：未配置时退回枚举名，由使用方按平台文档补齐。 */
    public String invoiceTypeCode(InvoiceType type) {
        if (type == null) {
            return null;
        }
        String code = invoiceTypeCodes == null ? null : invoiceTypeCodes.get(type.name());
        return code == null ? type.name() : code;
    }
}
