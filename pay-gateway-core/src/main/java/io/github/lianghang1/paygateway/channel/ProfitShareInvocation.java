package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.model.ProfitShareState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道分账发起结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareInvocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧分账单号（可空） */
    private String channelShareNo;
    /** 发起后的分账状态（FINISHED/FAILED/PROCESSING） */
    private ProfitShareState state;
}
