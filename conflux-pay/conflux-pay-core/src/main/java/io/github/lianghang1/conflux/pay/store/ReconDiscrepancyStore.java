package io.github.lianghang1.conflux.pay.store;

import io.github.lianghang1.conflux.pay.model.ReconDiscrepancy;

/**
 * 对账差错记录持久化 SPI。可选能力：未注册时对账发现的差错不落库。
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
}
