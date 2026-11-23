package com.sfi.conflux.pay.api;

import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务退款单号 */
    private String bizRefundNo;
    /** 退款金额（分） */
    private Long amount;
    /** 币种 */
    private String currency;
    /** 退款单状态 */
    private RefundState state;
    /** 渠道侧退款单号 */
    private String channelRefundNo;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
    /** 退款成功时间（可空） */
    private LocalDateTime succeedTime;

    /** 由退款单记录构建结果。 */
    public static RefundResult of(Refund refund) {
        return new RefundResult()
                .setConfigId(refund.getConfigId())
                .setBizRefundNo(refund.getBizRefundNo())
                .setAmount(refund.getAmount())
                .setCurrency(refund.getCurrency())
                .setState(refund.getState())
                .setChannelRefundNo(refund.getChannelRefundNo())
                .setErrorCode(refund.getErrorCode())
                .setErrorMsg(refund.getErrorMsg())
                .setSucceedTime(refund.getSucceedTime());
    }
}
