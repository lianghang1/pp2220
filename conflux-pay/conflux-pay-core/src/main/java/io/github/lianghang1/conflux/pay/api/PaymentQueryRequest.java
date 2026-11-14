package io.github.lianghang1.conflux.pay.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 支付单查询定位参数：{@code paymentId} 或 {@code appId + bizOrderNo} 二选一。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关支付单号 */
    private String paymentId;
    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务单号 */
    private String bizOrderNo;
}
