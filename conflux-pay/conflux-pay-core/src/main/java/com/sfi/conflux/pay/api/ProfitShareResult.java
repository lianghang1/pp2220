package com.sfi.conflux.pay.api;

import com.sfi.conflux.pay.model.ProfitShare;
import com.sfi.conflux.pay.model.ProfitShareReceiver;
import com.sfi.conflux.pay.model.ProfitShareState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分账结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务分账单号 */
    private String bizShareNo;
    /** 分账总金额（分） */
    private Long amount;
    /** 分账接收方列表（含渠道回填的单接收方结果） */
    private List<ProfitShareReceiver> receivers;
    /** 分账单状态 */
    private ProfitShareState state;
    /** 渠道侧分账单号 */
    private String channelShareNo;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
    /** 分账完成时间（可空） */
    private LocalDateTime finishTime;

    /** 由分账单记录构建结果。 */
    public static ProfitShareResult of(ProfitShare share) {
        return new ProfitShareResult()
                .setConfigId(share.getConfigId())
                .setBizShareNo(share.getBizShareNo())
                .setAmount(share.getAmount())
                .setReceivers(share.getReceivers())
                .setState(share.getState())
                .setChannelShareNo(share.getChannelShareNo())
                .setErrorCode(share.getErrorCode())
                .setErrorMsg(share.getErrorMsg())
                .setFinishTime(share.getFinishTime());
    }
}
