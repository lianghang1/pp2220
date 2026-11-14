package io.github.lianghang1.paygateway.api;

import io.github.lianghang1.paygateway.exception.GatewayAssert;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.model.ProfitShareReceiver;
import io.github.lianghang1.paygateway.util.CollUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 分账请求：将已成功支付订单的资金分给多个接收方。
 *
 * <p>分账对象通过 {@code paymentId} 或 {@code appId + bizOrderNo} 定位；
 * 同一 {@code appId + bizShareNo} 重复提交视为幂等。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务分账单号（appId 内唯一，必填） */
    private String bizShareNo;
    /** 网关支付单号（与 bizOrderNo 二选一） */
    private String paymentId;
    /** 使用方业务单号（与 paymentId 二选一） */
    private String bizOrderNo;
    /** 分账接收方列表（必填，合计金额需大于 0） */
    private List<ProfitShareReceiver> receivers;
    /** 下游通知地址（选填；分账终态后通知） */
    private String notifyUrl;
    /** 渠道附加参数（选填） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, GatewayErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizShareNo, GatewayErrorCode.BAD_REQUEST, "bizShareNo 不能为空");
        GatewayAssert.isTrue(paymentId != null || bizOrderNo != null,
                GatewayErrorCode.BAD_REQUEST, "paymentId 与 bizOrderNo 至少提供一个");
        GatewayAssert.isTrue(CollUtils.isNotEmpty(receivers), GatewayErrorCode.BAD_REQUEST, "receivers 不能为空");
        long total = 0L;
        for (ProfitShareReceiver receiver : receivers) {
            GatewayAssert.notBlank(receiver.getReceiverAccount(), GatewayErrorCode.BAD_REQUEST,
                    "接收方账户不能为空");
            GatewayAssert.notNull(receiver.getAmount(), GatewayErrorCode.BAD_REQUEST,
                    "接收方分账金额不能为空");
            GatewayAssert.isTrue(receiver.getAmount() > 0, GatewayErrorCode.BAD_REQUEST,
                    "接收方分账金额必须大于 0");
            total += receiver.getAmount();
        }
        GatewayAssert.isTrue(total > 0, GatewayErrorCode.BAD_REQUEST, "分账总金额必须大于 0");
    }
}
