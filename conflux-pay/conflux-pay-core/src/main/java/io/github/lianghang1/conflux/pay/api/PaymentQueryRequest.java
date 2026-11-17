package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道查单/关单定位参数：{@code appId + bizOrderNo + channelCode}。
 *
 * <p>SDK 无台账，渠道单号与渠道编码由使用方提供；{@code channelOrderNo}
 * 可选，已知时优先用于渠道定位。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务单号（必填，即渠道商户单号） */
    private String bizOrderNo;
    /** 渠道编码（必填） */
    private String channelCode;
    /** 渠道侧单号（选填，已知时优先使用） */
    private String channelOrderNo;

    /** 参数校验：不满足抛 {@link CommonErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, CommonErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizOrderNo, CommonErrorCode.BAD_REQUEST, "bizOrderNo 不能为空");
        GatewayAssert.notBlank(channelCode, CommonErrorCode.BAD_REQUEST, "channelCode 不能为空");
    }
}
