package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.pay.model.ReconDiscrepancy;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 对账报告：一次对账执行的统计与差错明细。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ReconReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String appId;
    /** 渠道编码 */
    private String channelCode;
    /** 对账日期 */
    private LocalDate billDate;
    /** 渠道账单条目数 */
    private int channelEntryCount;
    /** 比对一致条目数 */
    private int matchedCount;
    /** 差错明细（已同步写入 ReconDiscrepancyStore） */
    private List<ReconDiscrepancy> discrepancies;
}
