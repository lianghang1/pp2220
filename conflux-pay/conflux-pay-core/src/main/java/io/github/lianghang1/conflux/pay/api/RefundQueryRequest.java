package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道退款查单定位参数：{@code appId + bizRefundNo + channelCode}。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RefundQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务退款单号（必填，即渠道商户退款单号） */
    private String bizRefundNo;
    /** 渠道编码（必填） */
    private String channelCode;
    /** 渠道侧退款单号（选填，已知时优先使用） */
    private String channelRefundNo;
    /** 渠道侧回传的原单业务单号（选填，部分渠道查单需要） */
    private String bizOrderNo;

    /** 参数校验：不满足抛 {@link CommonErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, CommonErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizRefundNo, CommonErrorCode.BAD_REQUEST, "bizRefundNo 不能为空");
        GatewayAssert.notBlank(channelCode, CommonErrorCode.BAD_REQUEST, "channelCode 不能为空");
    }
}
