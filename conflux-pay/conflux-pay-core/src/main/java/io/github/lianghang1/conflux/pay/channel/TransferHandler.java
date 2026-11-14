package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.model.Transfer;

/**
 * 渠道转账能力：发起转账、查询转账。
 *
 * <p>转账为资金出账操作，实现方必须保证幂等（以业务转账单号为渠道幂等键），
 * 超时后先查询渠道侧结果再决定是否重发，杜绝重复出账。</p>
 *
 * @author lianghang
 */
public interface TransferHandler {

    /**
     * 发起渠道转账；返回渠道转账单号与当前状态。
     */
    TransferInvocation createTransfer(Transfer transfer, ChannelConfig config);

    /** 查询渠道侧转账状态：用于转账单补单。 */
    TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config);
}
