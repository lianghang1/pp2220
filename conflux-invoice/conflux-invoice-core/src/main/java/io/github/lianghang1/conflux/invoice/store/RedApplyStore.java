package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.RedApply;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 红字确认单持久化 SPI：同样使用 CAS 版本号，避免确认回调与轮询并发覆盖。
 *
 * @author lianghang
 */
public interface RedApplyStore {

    void save(RedApply apply);

    boolean tryUpdate(RedApply apply, long expectedVersion);

    RedApply findById(String redApplyId);

    RedApply findByBizRedNo(String appId, String bizRedNo);

    List<RedApply> findByOriginalInvoiceId(String originalInvoiceId);

    /** 查询超过确认时限仍挂起的确认单（过期扫描用）。 */
    List<RedApply> findWaitingConfirmBefore(LocalDateTime threshold, int limit);
}
