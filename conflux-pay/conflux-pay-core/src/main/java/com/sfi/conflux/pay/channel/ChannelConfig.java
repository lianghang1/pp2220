package com.sfi.conflux.pay.channel;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道配置基类（各渠道强类型 config 的父类）：承载框架理解的公共字段与个性 attr。
 *
 * <p>各渠道包定义 {@code XxxConfig extends ChannelConfig}：公共字段（configId/channelCode/
 * notifyUrl/sandbox）由框架装载时显式设置，渠道个性键放 {@link #attr}，由渠道自己的
 * {@code of(attr)} / {@code fromMap(attr)} 映射为强类型字段。框架对 attr 内容零感知。</p>
 *
 * <p>服务商模式特约商户号 subMchNo 不再作为独立公共字段——它属于渠道个性配置，
 * 放 attr 中（键 {@code subMchNo}），通过 {@link #getSubMchNo()} 从 attr 派生读取，
 * 兼容各渠道 handler 的服务商路由判断。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置标识（使用方自定义，用于定位一组渠道配置） */
    private String configId;
    /** 渠道编码 */
    private String channelCode;
    /** 渠道异步通知地址（统一键：全渠道同名语义） */
    private String notifyUrl;
    /** 沙箱开关（统一键：有官方沙箱/仿真系统的渠道据此切换网关） */
    private boolean sandbox;
    /** 渠道个性配置（原始键值对；键名由各渠道定义，映射进渠道强类型 config） */
    private Map<String, String> attr;

    /** 服务商模式特约商户号：从 attr 派生（不再作为独立公共字段）。 */
    public String getSubMchNo() {
        return attr == null ? null : attr.get("subMchNo");
    }
}
