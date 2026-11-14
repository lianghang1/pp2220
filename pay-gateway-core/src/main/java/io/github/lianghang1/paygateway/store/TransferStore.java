package io.github.lianghang1.paygateway.store;

import io.github.lianghang1.paygateway.model.Transfer;

/**
 * 转账单持久化 SPI。
 *
 * <p>建议索引：{@code appId + bizTransferNo} 唯一（幂等键）、{@code transferId} 主键。</p>
 *
 * @author lianghang
 */
public interface TransferStore {

    /** 新增转账单。 */
    void save(Transfer transfer);

    /** 全量更新转账单（按 transferId 覆盖）。 */
    void update(Transfer transfer);

    /** 按网关转账单号查询；不存在返回 null。 */
    Transfer findById(String transferId);

    /** 按业务转账单号查询（幂等键）；不存在返回 null。 */
    Transfer findByBizTransferNo(String appId, String bizTransferNo);
}
