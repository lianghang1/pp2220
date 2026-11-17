package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 红字确认单「主动确认 / 拒绝」请求（与查询确认状态分开）。
 *
 * <p>查询状态是 {@code queryRedApply(redApplyId)} 的语义：向渠道确认对方是否已确认；
 * 本对象是动作语义：我方作为确认方主动同意或拒绝（渠道需提供该能力）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedConfirmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    /** 红字确认单号（必填）。 */
    private String redApplyId;
    /** true=同意，false=拒绝。 */
    private boolean accept;
    /** 拒绝原因（拒绝时建议填写）。 */
    private String reason;
    /** 渠道自定义参数。 */
    private Map<String, String> extra;
}
