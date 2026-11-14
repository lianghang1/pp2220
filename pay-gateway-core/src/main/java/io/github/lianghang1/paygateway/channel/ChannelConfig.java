package io.github.lianghang1.paygateway.channel;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道配置：一次渠道调用的完整配置上下文。
 *
 * <p>由 {@code ChannelConfigProvider} 按 appId + 渠道编码构建并缓存（实现方决定缓存策略）；
 * 统一基础键（appId/channelCode/notifyUrl/sandbox/subMchNo）由框架承载，
 * {@code params} 为渠道自定义参数对象（实现 {@link ChannelParams} 的类型，
 * 承载渠道个性键，由渠道包内向下转型使用）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String appId;
    /** 渠道编码 */
    private String channelCode;
    /** 渠道异步通知地址（统一键：全渠道同名语义） */
    private String notifyUrl;
    /** 沙箱开关（统一键：有官方沙箱/仿真系统的渠道据此切换网关） */
    private boolean sandbox;
    /** 服务商模式下的特约商户号；普通商户模式为 null */
    private String subMchNo;
    /** 渠道自定义参数对象 */
    private ChannelParams params;
}
