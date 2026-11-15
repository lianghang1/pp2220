package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 可冲销明细查询结果（红冲前置校验用）。
 *
 * <p>渠道不支持该能力时返回 {@code null}，编排层跳过校验，不构成强制契约。
 * 部分渠道（如百旺 3.3.17）该接口是异步的，此时 {@link #asyncAccepted}=true，
 * 结果走回调，编排层不据此阻断申请。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedAvailableResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否允许发起红冲（false 时编排层阻断并抛 STATE_ILLEGAL）。 */
    private boolean available = true;
    /** 渠道是否以异步方式受理（真实结果走回调）。 */
    private boolean asyncAccepted;
    /** 可红冲金额（渠道能同步给出时填写）。 */
    private BigDecimal availableAmount;
    /** 渠道侧申请流水号。 */
    private String channelOrderNo;
    /** 明细行摘要（可选，渠道自定义结构）。 */
    private List<Map<String, Object>> items;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文。 */
    private String rawPayload;
}
