package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.pay.model.BillEntryType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道账单条目：渠道账单中单笔流水的归一化表达。
 *
 * <p>渠道包负责把渠道原始流水（CSV/接口返回）翻译为本结构；
 * {@code success} 为归一化后的成功标记，原始状态保留在 {@code rawState}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class BillEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流水类型（支付/退款） */
    private BillEntryType entryType;
    /** 渠道侧单号 */
    private String channelOrderNo;
    /** 渠道侧回传的业务单号（即我方业务单号/退款单号） */
    private String bizOrderNo;
    /** 金额（分） */
    private Long amount;
    /** 归一化成功标记 */
    private boolean success;
    /** 原始状态描述（渠道语义，留痕用） */
    private String rawState;
    /** 交易发生时间 */
    private LocalDateTime occurTime;
}
