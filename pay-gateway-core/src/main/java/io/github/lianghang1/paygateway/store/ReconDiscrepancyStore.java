package io.github.lianghang1.paygateway.store;

import io.github.lianghang1.paygateway.model.ReconDiscrepancy;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账差错记录持久化 SPI。
 *
 * <p>建议索引：{@code billDate + channelCode} 组合索引、{@code reconId} 主键。</p>
 *
 * @author lianghang
 */
public interface ReconDiscrepancyStore {

    /** 新增差错记录。 */
    void save(ReconDiscrepancy discrepancy);

    /** 更新差错记录（人工处理标记等，按 reconId 覆盖）。 */
    void update(ReconDiscrepancy discrepancy);

    /** 查询指定渠道、指定账单日期的全部差错记录。 */
    List<ReconDiscrepancy> findByDate(String channelCode, String appId, LocalDate billDate);
}
