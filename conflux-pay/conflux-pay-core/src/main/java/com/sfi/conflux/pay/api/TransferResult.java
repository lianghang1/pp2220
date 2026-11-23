package com.sfi.conflux.pay.api;

import com.sfi.conflux.pay.model.Transfer;
import com.sfi.conflux.pay.model.TransferState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转账结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务转账单号 */
    private String bizTransferNo;
    /** 转账方式编码 */
    private String method;
    /** 渠道编码 */
    private String channelCode;
    /** 转账单状态 */
    private TransferState state;
    /** 渠道侧转账单号 */
    private String channelTransferNo;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
    /** 转账成功时间（可空） */
    private LocalDateTime succeedTime;

    /** 由转账单记录构建结果。 */
    public static TransferResult of(Transfer transfer) {
        return new TransferResult()
                .setConfigId(transfer.getConfigId())
                .setBizTransferNo(transfer.getBizTransferNo())
                .setMethod(transfer.getMethod())
                .setChannelCode(transfer.getChannelCode())
                .setState(transfer.getState())
                .setChannelTransferNo(transfer.getChannelTransferNo())
                .setErrorCode(transfer.getErrorCode())
                .setErrorMsg(transfer.getErrorMsg())
                .setSucceedTime(transfer.getSucceedTime());
    }
}
