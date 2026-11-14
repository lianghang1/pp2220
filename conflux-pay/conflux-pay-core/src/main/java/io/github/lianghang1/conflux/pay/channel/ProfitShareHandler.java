package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.model.ProfitShare;

/**
 * 渠道分账能力：发起分账、查询分账。
 *
 * <p>分账明细的接收方结果（成功/失败、失败原因）由实现方回填到
 * {@code ProfitShare.receivers} 各项的状态字段上。</p>
 *
 * @author lianghang
 */
public interface ProfitShareHandler {

    /**
     * 发起渠道分账；返回渠道分账单号与当前状态。
     */
    ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config);

    /** 查询渠道侧分账状态：用于分账单补单。 */
    ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config);

    /**
     * 绑定分账接收方（渠道要求先绑定后分账的渠道必须实现，如微信）。
     * 默认抛「渠道不支持该能力」——渠道无绑定要求的可不实现。
     */
    default void bindReceiver(io.github.lianghang1.conflux.pay.model.ProfitShareReceiver receiver,
                              ChannelConfig config) {
        throw new io.github.lianghang1.conflux.common.exception.GatewayException(
                io.github.lianghang1.conflux.pay.exception.GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "渠道不支持分账接收方绑定");
    }

    /** 解绑分账接收方；默认抛「渠道不支持该能力」。 */
    default void unbindReceiver(io.github.lianghang1.conflux.pay.model.ProfitShareReceiver receiver,
                                ChannelConfig config) {
        throw new io.github.lianghang1.conflux.common.exception.GatewayException(
                io.github.lianghang1.conflux.pay.exception.GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "渠道不支持分账接收方解绑");
    }
}
