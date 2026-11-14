package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.api.PayPayload;
import io.github.lianghang1.paygateway.model.PaymentState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道支付发起结果：渠道 Handler 调用后的归一化返回。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentInvocation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧单号（下单阶段即返回单号的渠道回填，可空） */
    private String channelOrderNo;
    /** 支付唤起参数（即时扣款方式为 NONE，可空） */
    private PayPayload payload;
    /** 发起后的订单状态（常规方式 PAYING；即时扣款成功 SUCCEEDED） */
    private PaymentState state;
}
