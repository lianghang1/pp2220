package com.sfi.conflux.invoice.api;

import com.sfi.conflux.common.exception.GatewayAssert;
import com.sfi.conflux.invoice.model.Invoice;
import com.sfi.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 红字发票开具请求（两段红冲的第二段）。
 *
 * <p>SDK 无台账——确认单载体与原票载体由使用方回传；是否可开具（CONFIRMED、
 * 未过期）由渠道侧把关，SDK 不越位校验业务状态。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 指定渠道（为空取确认单载体的渠道） */
    private String channelCode;
    /** 渠道回调地址（可选） */
    private String notifyUrl;
    /** 使用方保管的红字确认单载体（必填） */
    private RedApply redApply;
    /** 原蓝字发票载体（红票票面要素来源，必填） */
    private Invoice originalInvoice;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(redApply != null, "redApply 确认单载体不能为空");
        GatewayAssert.param(originalInvoice != null, "originalInvoice 原票载体不能为空");
    }
}
