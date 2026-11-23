package com.sfi.conflux.pay.channel;

import com.sfi.conflux.pay.model.RefundState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道退款发起结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundInvocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧退款单号（可空） */
    private String channelRefundNo;
    /** 发起后的退款状态（SUCCEEDED/FAILED/PROCESSING） */
    private RefundState state;
}
