package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 发票渠道配置：一次渠道调用的完整上下文。
 *
 * <p>相较支付域多出 {@link #sellerTaxNo}（企业准入标识）与 {@link #tier}
 * （通道分级）：同一渠道下同一企业可能有多套凭据/多级通道，路由与回调反查
 * 都必须带上这两个维度。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    private String channelCode;
    /** 渠道实例标识。 */
    private String channelInstanceId;
    /** 回调反查唯一键，由 InvoiceAccountKey.of(channelAppId, sellerTaxNo) 生成。 */
    private String accountKey;
    /** 销方纳税人识别号。 */
    private String sellerTaxNo;
    private String notifyUrl;
    private boolean sandbox;
    /** 通道分级。 */
    private ChannelTier tier;
    /** 渠道自定义参数对象。 */
    private InvoiceChannelParams params;
    /** 渠道个性配置（原始键值对，便于渠道自行读取）。 */
    private Map<String, String> attrs;
}
