package io.github.lianghang1.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 退款单：关联支付单的部分/全额退回记录。
 *
 * <p>一个支付单可对应多条退款单；累计退款成功金额达到支付金额时，
 * 支付单状态迁移为 REFUNDED，否则为 PART_REFUNDED。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class Refund implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务退款单号（渠道商户单号，渠道侧幂等键；查重由使用方负责） */
    private String bizRefundNo;
    /** 关联的使用方业务单号（冗余，便于查询） */
    private String bizOrderNo;
    /** 渠道编码（继承自支付单） */
    private String channelCode;
    /** 退款金额（分） */
    private Long amount;
    /** 币种 */
    private String currency;
    /** 退款原因 */
    private String reason;
    /** 异步通知地址（按单覆盖渠道配置）；请求优先、配置兜底，由渠道 handler 读取 */
    private String notifyUrl;
    /** 退款单状态 */
    private RefundState state;
    /** 渠道侧退款单号 */
    private String channelRefundNo;
    /** 渠道/网关错误码（失败时记录） */
    private String errorCode;
    /** 错误信息 */
    private String errorMsg;
    /** 退款成功时间 */
    private LocalDateTime succeedTime;
    /** 扩展参数（JSON） */
    private String extParams;
}
