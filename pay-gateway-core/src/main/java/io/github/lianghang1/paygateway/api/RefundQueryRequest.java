package io.github.lianghang1.paygateway.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 退款单查询定位参数：{@code refundId} 或 {@code appId + bizRefundNo} 二选一。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关退款单号 */
    private String refundId;
    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务退款单号 */
    private String bizRefundNo;
}
