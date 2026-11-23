package com.sfi.conflux.pay.api;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.common.exception.GatewayAssert;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道转账查单定位参数：{@code configId + bizTransferNo + channelCode}。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 使用方业务转账单号（必填，即渠道商户转账单号） */
    private String bizTransferNo;
    /** 渠道编码（必填） */
    private String channelCode;
    /** 渠道侧转账单号（选填，已知时优先使用） */
    private String channelTransferNo;

    /** 参数校验：不满足抛 {@link CommonErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(configId, CommonErrorCode.BAD_REQUEST, "configId 不能为空");
        GatewayAssert.notBlank(bizTransferNo, CommonErrorCode.BAD_REQUEST, "bizTransferNo 不能为空");
        GatewayAssert.notBlank(channelCode, CommonErrorCode.BAD_REQUEST, "channelCode 不能为空");
    }
}
