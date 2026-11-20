package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 发票交付（短信 / 邮箱重发）请求。
 *
 * <p>SDK 无台账——发票载体由使用方随 {@code invoice} 回传，SDK 组装渠道上下文
 * 透传交付能力。手机号与邮箱至少提供其一，由渠道实现校验。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class DeliveryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 接收手机号（单个） */
    private String phone;
    /** 接收邮箱，多个以英文逗号分隔 */
    private String email;
    /** 渠道自定义参数 */
    private Map<String, String> extra;
    /** 使用方保管的发票载体（必填） */
    private Invoice invoice;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(phone != null && phone.trim().length() > 0
                || email != null && email.trim().length() > 0, "手机号或邮箱至少提供一个");
        GatewayAssert.param(invoice != null, "invoice 发票载体不能为空");
    }
}
