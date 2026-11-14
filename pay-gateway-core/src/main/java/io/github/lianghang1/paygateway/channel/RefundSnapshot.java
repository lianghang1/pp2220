package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.model.RefundState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道侧退款快照：查单返回的归一化状态。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧退款单号 */
    private String channelRefundNo;
    /** 渠道侧退款状态（SUCCEEDED/FAILED/PROCESSING/CLOSED） */
    private RefundState state;
    /** 渠道侧成功时间（可空） */
    private LocalDateTime succeedTime;
    /** 渠道原始状态/报文摘要（可空） */
    private String rawMsg;
}
