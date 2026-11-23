package com.sfi.conflux.pay.api;

import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一下单结果。
 *
 * <p>state 为 PAYING 时表示已取得唤起参数、等待用户支付；为 SUCCEEDED 时
 * 表示即时扣款类方式已完成支付；CREATED 表示渠道调用尚未成功（可携带相同
 * 参数重试下单）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务单号 */
    private String bizOrderNo;
    /** 支付方式编码 */
    private String method;
    /** 渠道编码 */
    private String channelCode;
    /** 渠道侧单号（渠道未返回时为空） */
    private String channelOrderNo;
    /** 支付单状态 */
    private PaymentState state;
    /** 支付唤起参数（可空） */
    private PayPayload payPayload;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
    /** 支付成功时间（可空） */
    private LocalDateTime succeedTime;

    /** 由支付单记录构建结果。 */
    public static PaymentResult of(Payment payment) {
        return new PaymentResult()
                .setConfigId(payment.getConfigId())
                .setBizOrderNo(payment.getBizOrderNo())
                .setMethod(payment.getMethod())
                .setChannelCode(payment.getChannelCode())
                .setChannelOrderNo(payment.getChannelOrderNo())
                .setState(payment.getState())
                .setPayPayload(PayPayloadReader.read(payment.getPayPayload()))
                .setErrorCode(payment.getErrorCode())
                .setErrorMsg(payment.getErrorMsg())
                .setSucceedTime(payment.getSucceedTime());
    }
}
