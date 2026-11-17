package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.RedApply;

/**
 * 红字确认单持久化 SPI：按主键全量覆盖写入，并发控制由使用方保证。
 *
 * @author lianghang
 */
public interface RedApplyStore {

    void save(RedApply apply);

    void update(RedApply apply);

    RedApply findById(String redApplyId);

    RedApply findByBizRedNo(String appId, String bizRedNo);
}
