package com.sfi.conflux.invoice.api;

import com.sfi.conflux.invoice.model.RedApplyState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道红字确认单结果（申请/查询共用）。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedApplyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private RedApplyState state;
    /** 渠道侧申请流水号。 */
    private String channelOrderNo;
    /** 红字确认单编号。 */
    private String redConfirmNo;
    /** 对方确认截止时间。 */
    private LocalDateTime confirmDeadline;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文。 */
    private String rawPayload;

    public static RedApplyResult waiting(String channelOrderNo, LocalDateTime deadline) {
        return new RedApplyResult().setState(RedApplyState.WAITING_CONFIRM)
                .setChannelOrderNo(channelOrderNo).setConfirmDeadline(deadline);
    }

    public static RedApplyResult confirmed(String redConfirmNo) {
        return new RedApplyResult().setState(RedApplyState.CONFIRMED)
                .setRedConfirmNo(redConfirmNo);
    }

    public static RedApplyResult failed(String errorCode, String errorMsg) {
        return new RedApplyResult().setState(RedApplyState.FAILED)
                .setErrorCode(errorCode).setErrorMsg(errorMsg);
    }
}
