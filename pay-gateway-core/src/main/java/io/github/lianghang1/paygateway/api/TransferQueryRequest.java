package io.github.lianghang1.paygateway.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 转账单查询定位参数：{@code transferId} 或 {@code appId + bizTransferNo} 二选一。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关转账单号 */
    private String transferId;
    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务转账单号 */
    private String bizTransferNo;
}
