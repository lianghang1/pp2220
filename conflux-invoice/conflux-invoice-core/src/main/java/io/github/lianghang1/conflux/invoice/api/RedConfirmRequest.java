package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 红字确认单「主动确认 / 拒绝」请求（与查询确认状态分开）。
 *
 * <p>SDK 无台账——确认单载体由使用方随 {@code redApply} 回传（applyRed/quickRed
 * 已把确认单返回给使用方保管）；本对象是动作语义：主动同意或拒绝。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedConfirmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** true=同意，false=拒绝 */
    private boolean accept;
    /** 拒绝原因（拒绝时建议填写） */
    private String reason;
    /** 渠道自定义参数 */
    private Map<String, String> extra;
    /** 使用方保管的红字确认单载体（必填，含 redConfirmNo/channelOrderNo） */
    private RedApply redApply;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(redApply != null, "redApply 确认单载体不能为空");
    }
}
