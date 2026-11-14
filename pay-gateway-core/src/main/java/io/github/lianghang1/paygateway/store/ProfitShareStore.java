package io.github.lianghang1.paygateway.store;

import io.github.lianghang1.paygateway.model.ProfitShare;

/**
 * 分账单持久化 SPI。
 *
 * <p>建议索引：{@code appId + bizShareNo} 唯一（幂等键）、{@code paymentId} 普通索引、
 * {@code shareId} 主键；receivers 明细建议以 JSON 列随单存储。</p>
 *
 * @author lianghang
 */
public interface ProfitShareStore {

    /** 新增分账单。 */
    void save(ProfitShare share);

    /** 全量更新分账单（按 shareId 覆盖）。 */
    void update(ProfitShare share);

    /** 按网关分账单号查询；不存在返回 null。 */
    ProfitShare findById(String shareId);

    /** 按业务分账单号查询（幂等键）；不存在返回 null。 */
    ProfitShare findByBizShareNo(String appId, String bizShareNo);
}
