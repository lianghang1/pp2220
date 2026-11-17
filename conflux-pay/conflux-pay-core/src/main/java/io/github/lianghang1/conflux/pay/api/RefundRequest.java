package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 退款请求。
 *
 * <p>SDK 无台账，原单信息由使用方随请求提供：渠道编码、原单业务单号、
 * 原单金额（渠道要求）、原单渠道单号（已知时提供，优先用于渠道定位）。
 * 可退余额校验为使用方业务规则，SDK 不校验；渠道以 {@code bizRefundNo}
 * 为幂等键，重复提交由渠道侧保证一致。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务退款单号（appId 内唯一，必填） */
    private String bizRefundNo;
    /** 渠道编码（必填，须与原支付单一致） */
    private String channelCode;
    /** 使用方业务单号（原支付单的业务单号，必填） */
    private String bizOrderNo;
    /** 原单渠道侧单号（选填，已知时优先使用） */
    private String channelOrderNo;
    /** 原单支付金额（分，必填，部分渠道退款必传） */
    private Long totalAmount;
    /** 退款金额（分，必填，大于 0 且不超过可退余额） */
    private Long amount;
    /** 币种（选填，默认跟随原单） */
    private String currency;
    /** 退款原因 */
    private String reason;
    /** 渠道附加参数（选填） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, CommonErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizRefundNo, CommonErrorCode.BAD_REQUEST, "bizRefundNo 不能为空");
        GatewayAssert.notBlank(channelCode, CommonErrorCode.BAD_REQUEST, "channelCode 不能为空");
        GatewayAssert.notBlank(bizOrderNo, CommonErrorCode.BAD_REQUEST, "bizOrderNo 不能为空");
        GatewayAssert.notNull(totalAmount, CommonErrorCode.BAD_REQUEST, "totalAmount 不能为空");
        GatewayAssert.isTrue(totalAmount > 0, CommonErrorCode.BAD_REQUEST, "totalAmount 必须大于 0");
        GatewayAssert.notNull(amount, CommonErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, CommonErrorCode.BAD_REQUEST, "amount 必须大于 0");
    }
}
