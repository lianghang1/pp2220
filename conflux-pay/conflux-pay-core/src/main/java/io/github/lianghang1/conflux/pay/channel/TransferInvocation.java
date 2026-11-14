package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.model.TransferState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道转账发起结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferInvocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧转账单号（可空） */
    private String channelTransferNo;
    /** 发起后的转账状态（SUCCEEDED/FAILED/PROCESSING） */
    private TransferState state;
}
