package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.pay.model.ProfitShareReceiver;
import io.github.lianghang1.conflux.common.util.CollUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 分账请求：将已成功支付订单的资金分给多个接收方。
 *
 * <p>SDK 无台账，原支付单的渠道编码与渠道侧单号由使用方随请求提供；
 * 是否允许分账（如订单已成功支付、分账比例限制）为使用方业务规则。
 * 渠道以 {@code bizShareNo} 为幂等键，重复提交由渠道侧保证一致。</p>
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
    /** 渠道编码（必填，须与原支付单一致） */
    private String channelCode;
    /** 使用方业务单号（原支付单的业务单号，选填留痕） */
    private String bizOrderNo;
    /** 原支付单渠道侧单号（必填，渠道分账以此关联原单） */
    private String channelOrderNo;
    /** 分账接收方列表（必填，合计金额需大于 0） */
    private List<ProfitShareReceiver> receivers;
    /** 渠道附加参数（选填） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, CommonErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizShareNo, CommonErrorCode.BAD_REQUEST, "bizShareNo 不能为空");
        GatewayAssert.notBlank(channelCode, CommonErrorCode.BAD_REQUEST, "channelCode 不能为空");
        GatewayAssert.notBlank(channelOrderNo, CommonErrorCode.BAD_REQUEST, "channelOrderNo 不能为空");
        GatewayAssert.isTrue(CollUtils.isNotEmpty(receivers), CommonErrorCode.BAD_REQUEST, "receivers 不能为空");
        long total = 0L;
        for (ProfitShareReceiver receiver : receivers) {
            GatewayAssert.notBlank(receiver.getReceiverAccount(), CommonErrorCode.BAD_REQUEST,
                    "接收方账户不能为空");
            GatewayAssert.notNull(receiver.getAmount(), CommonErrorCode.BAD_REQUEST,
                    "接收方分账金额不能为空");
            GatewayAssert.isTrue(receiver.getAmount() > 0, CommonErrorCode.BAD_REQUEST,
                    "接收方分账金额必须大于 0");
            total += receiver.getAmount();
        }
        GatewayAssert.isTrue(total > 0, CommonErrorCode.BAD_REQUEST, "分账总金额必须大于 0");
    }
}
