package io.github.lianghang1.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分账单：将已成功支付订单的资金分给多个接收方的记录。
 *
 * <p>{@code receivers} 为分账接收方列表，持久化时建议以 JSON 列存储；
 * 分账发起前提是关联支付单已成功。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShare implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务分账单号（渠道商户单号，渠道侧幂等键；查重由使用方负责） */
    private String bizShareNo;
    /** 关联的使用方业务单号（冗余） */
    private String bizOrderNo;
    /** 渠道编码（继承自支付单） */
    private String channelCode;
    /** 关联支付单的渠道侧单号（分账 API 必需的入账凭证） */
    private String channelPayOrderNo;
    /** 分账总金额（分） */
    private Long amount;
    /** 分账接收方列表 */
    private List<ProfitShareReceiver> receivers;
    /** 分账单状态 */
    private ProfitShareState state;
    /** 渠道侧分账单号 */
    private String channelShareNo;
    /** 渠道/网关错误码（失败时记录） */
    private String errorCode;
    /** 错误信息 */
    private String errorMsg;
    /** 分账完成时间 */
    private LocalDateTime finishTime;
    /** 扩展参数（JSON） */
    private String extParams;
}
