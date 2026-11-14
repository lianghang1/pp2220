package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.model.PaymentState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道侧订单快照：查单返回的归一化状态，供补单与对账修正本地状态。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道侧单号 */
    private String channelOrderNo;
    /** 渠道侧订单状态（PAYING/SUCCEEDED/FAILED/CLOSED） */
    private PaymentState state;
    /** 渠道侧实付金额（分，渠道未返回时为 null） */
    private Long paidAmount;
    /** 渠道侧成功时间（可空） */
    private LocalDateTime succeedTime;
    /** 渠道原始状态/报文摘要（留痕用，可空） */
    private String rawMsg;
}
