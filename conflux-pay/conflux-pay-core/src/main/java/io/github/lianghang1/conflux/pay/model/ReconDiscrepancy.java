package io.github.lianghang1.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账差错记录：渠道账单与本地订单比对结果的留痕。
 *
 * <p>状态不一致（{@code STATE_MISMATCH}）在记录之外还会触发本地状态自动修正
 * （以渠道账单为准），修正动作产生的订单事件与下游通知照常发布。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ReconDiscrepancy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 差错记录号 */
    private String reconId;
    /** 使用方应用标识 */
    private String appId;
    /** 渠道编码 */
    private String channelCode;
    /** 对账日期（账单日期） */
    private LocalDate billDate;
    /** 差错类型 */
    private DiscrepancyType discrepancyType;
    /** 流水类型（支付/退款） */
    private BillEntryType entryType;
    /** 渠道侧单号 */
    private String channelOrderNo;
    /** 使用方业务单号 */
    private String bizOrderNo;
    /** 本地网关单号（本地存在时回填） */
    private String localOrderNo;
    /** 渠道侧金额（分，渠道无流水时为 null） */
    private Long channelAmount;
    /** 本地金额（分，本地无订单时为 null） */
    private Long localAmount;
    /** 渠道侧状态（原文，可空） */
    private String channelState;
    /** 本地状态（可空） */
    private String localState;
    /** 是否已人工处理 */
    private boolean handled;
    /** 备注（自动修正动作的说明等） */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createdTime;
    /** 更新时间 */
    private LocalDateTime updatedTime;
}
