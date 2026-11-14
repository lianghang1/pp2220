package io.github.lianghang1.paygateway.api;

import io.github.lianghang1.paygateway.exception.GatewayAssert;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 退款请求。
 *
 * <p>退款对象通过 {@code paymentId} 或 {@code appId + bizOrderNo} 定位；
 * 同一 {@code appId + bizRefundNo} 重复提交视为幂等（关联订单与金额一致时返回既有退款单）。</p>
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
    /** 网关支付单号（与 bizOrderNo 二选一） */
    private String paymentId;
    /** 使用方业务单号（与 paymentId 二选一） */
    private String bizOrderNo;
    /** 退款金额（分，必填，大于 0 且不超过可退余额） */
    private Long amount;
    /** 币种（选填，默认跟随支付单） */
    private String currency;
    /** 退款原因 */
    private String reason;
    /** 下游通知地址（选填；退款终态后通知） */
    private String notifyUrl;
    /** 渠道附加参数（选填） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, GatewayErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizRefundNo, GatewayErrorCode.BAD_REQUEST, "bizRefundNo 不能为空");
        GatewayAssert.isTrue(StringUtils.isNotBlank(paymentId) || StringUtils.isNotBlank(bizOrderNo),
                GatewayErrorCode.BAD_REQUEST, "paymentId 与 bizOrderNo 至少提供一个");
        GatewayAssert.notNull(amount, GatewayErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, GatewayErrorCode.BAD_REQUEST, "amount 必须大于 0");
    }
}
