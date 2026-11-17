package io.github.lianghang1.conflux.pay.store;

import io.github.lianghang1.conflux.pay.model.Transfer;

/**
 * 转账单持久化 SPI。可选能力：未注册时转账入口报 STORE_MISSING。
 *
 * <p>建议索引：{@code appId + bizTransferNo} 普通（业务单号查询）、{@code transferId} 主键。</p>
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
