package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道实例：一个「渠道 + 企业（销方税号）+ 通道级别」的可路由单元。
 *
 * <p>支付域按 appId + channelCode 二维取配置；发票域因为存在企业准入与
 * 通道分级，需要第三、第四个维度：同一 appId 下可能有多个税号，同一税号
 * 又可能同时具备乐企直连与服务商 API 两条通道，路由时按
 * {@link ChannelTier#getPriority()} 与 {@link #priority} 择优、失败降级。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String appId;
    private String channelCode;
    private String accountKey;
    private String sellerTaxNo;
    private ChannelTier tier;
    /** 同级别内的权重，数值越大越优先。 */
    private int priority;
    private boolean enabled;
    /** 该实例对应的完整配置。 */
    private ChannelConfig config;

    /** 综合排序权重：通道级别优先，其次实例权重。 */
    public int routingScore() {
        return (tier == null ? 0 : tier.getPriority()) * 1000 + priority;
    }
}
