package io.github.lianghang1.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转账单：渠道账户向收款方转账的记录。
 *
 * <p>{@code payeeAccount} 为收款账户（渠道语义各异：微信号/openid、支付宝登录号、
 * 银行卡号等）；日志与展示场景应经 {@code MaskUtils} 打码。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class Transfer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务转账单号（渠道商户单号，渠道侧幂等键；查重由使用方负责） */
    private String bizTransferNo;
    /** 转账方式编码（PayMethod.code，如 WECHAT_TRANS / ALI_TRANSFER） */
    private String method;
    /** 渠道编码 */
    private String channelCode;
    /** 收款方账户（打码后落日志） */
    private String payeeAccount;
    /** 收款方姓名（部分渠道用于实名校验，可空） */
    private String payeeName;
    /** 转账金额（分） */
    private Long amount;
    /** 币种 */
    private String currency;
    /** 转账备注/附言 */
    private String remark;
    /** 转账单状态 */
    private TransferState state;
    /** 渠道侧转账单号 */
    private String channelTransferNo;
    /** 渠道/网关错误码（失败时记录） */
    private String errorCode;
    /** 错误信息 */
    private String errorMsg;
    /** 转账成功时间 */
    private LocalDateTime succeedTime;
    /** 扩展参数（JSON） */
    private String extParams;
}
