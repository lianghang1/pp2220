package com.sfi.conflux.pay.channel;

import com.sfi.conflux.pay.model.ProfitShareState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道侧分账快照：查单返回的归一化状态。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧分账单号 */
    private String channelShareNo;
    /** 渠道侧分账状态（FINISHED/FAILED/PROCESSING） */
    private ProfitShareState state;
    /** 渠道侧完成时间（可空） */
    private LocalDateTime finishTime;
    /** 渠道原始状态/报文摘要（可空） */
    private String rawMsg;
}
