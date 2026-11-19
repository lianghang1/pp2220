package io.github.lianghang1.conflux.sms.channel;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 短信渠道配置：一次渠道调用的完整配置上下文。
 *
 * <p>由 {@code SmsConfigProvider} 按 appId + 渠道编码构建；
 * {@code params} 为渠道自定义参数对象，由渠道包内向下转型使用。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class SmsChannelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String appId;
    /** 渠道编码 */
    private String channelCode;
    /** 渠道自定义参数对象 */
    private SmsParams params;
}
