package com.sfi.conflux.invoice.api;

import com.sfi.conflux.common.exception.GatewayAssert;
import com.sfi.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 红字确认单状态查询请求。
 *
 * <p>SDK 无台账——确认单载体由使用方原样回传，SDK 只组装渠道上下文透传查询。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedApplyQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 使用方保管的红字确认单载体（必填，含 redConfirmNo/channelOrderNo） */
    private RedApply redApply;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(redApply != null, "redApply 确认单载体不能为空");
    }
}
