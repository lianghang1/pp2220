package io.github.lianghang1.conflux.pay.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 分账单查询定位参数：{@code shareId} 或 {@code appId + bizShareNo} 二选一。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关分账单号 */
    private String shareId;
    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务分账单号 */
    private String bizShareNo;
}
