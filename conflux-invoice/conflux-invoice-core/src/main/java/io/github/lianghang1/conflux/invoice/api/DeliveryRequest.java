package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 发票交付（短信 / 邮箱重发）请求。
 *
 * <p>渠道不支持交付能力时由注册表抛 {@code CHANNEL_CAPABILITY_NOT_SUPPORTED}。
 * 手机号与邮箱至少提供其一，由渠道实现自行校验。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class DeliveryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    /** 网关单据号（必填）。 */
    private String invoiceId;
    /** 接收手机号（单个）。 */
    private String phone;
    /** 接收邮箱，多个以英文逗号分隔。 */
    private String email;
    /** 渠道自定义参数。 */
    private Map<String, String> extra;
}
