package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.model.TransferState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道侧转账快照：查单返回的归一化状态。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧转账单号 */
    private String channelTransferNo;
    /** 渠道侧转账状态（SUCCEEDED/FAILED/PROCESSING） */
    private TransferState state;
    /** 渠道侧成功时间（可空） */
    private LocalDateTime succeedTime;
    /** 渠道原始状态/报文摘要（可空） */
    private String rawMsg;
}
