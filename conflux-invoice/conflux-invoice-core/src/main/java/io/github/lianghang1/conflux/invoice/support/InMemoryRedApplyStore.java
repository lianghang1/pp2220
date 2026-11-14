package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 红字确认单内存实现（CAS 版本号）。仅供单测与 example 演示。
 *
 * @author lianghang
 */
public class InMemoryRedApplyStore implements RedApplyStore {

    private final Map<String, RedApply> byId = new ConcurrentHashMap<String, RedApply>();
    private final Map<String, String> bizIndex = new ConcurrentHashMap<String, String>();
    private final Object lock = new Object();

    @Override
    public void save(RedApply apply) {
        synchronized (lock) {
            String bizKey = StoreSupport.key(apply.getAppId(), apply.getBizRedNo());
            if (bizIndex.containsKey(bizKey)) {
                throw new GatewayException(InvoiceErrorCode.RED_APPLY_STATE_ILLEGAL,
                        "红冲业务单号已存在: {}", apply.getBizRedNo());
            }
            RedApply stored = StoreSupport.copy(apply);
            byId.put(stored.getRedApplyId(), stored);
            bizIndex.put(bizKey, stored.getRedApplyId());
        }
    }

    @Override
    public boolean tryUpdate(RedApply apply, long expectedVersion) {
        synchronized (lock) {
            RedApply current = byId.get(apply.getRedApplyId());
            if (current == null || current.getVersion() != expectedVersion) {
                return false;
            }
            RedApply stored = StoreSupport.copy(apply);
            byId.put(stored.getRedApplyId(), stored);
            bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizRedNo()),
                    stored.getRedApplyId());
            return true;
        }
    }

    @Override
    public RedApply findById(String redApplyId) {
        return StoreSupport.copy(byId.get(redApplyId));
    }

    @Override
    public RedApply findByBizRedNo(String appId, String bizRedNo) {
        String id = bizIndex.get(StoreSupport.key(appId, bizRedNo));
        return id == null ? null : findById(id);
    }

    @Override
    public List<RedApply> findByOriginalInvoiceId(String originalInvoiceId) {
        List<RedApply> result = new ArrayList<RedApply>();
        for (RedApply apply : byId.values()) {
            if (originalInvoiceId != null
                    && originalInvoiceId.equals(apply.getOriginalInvoiceId())) {
                result.add(StoreSupport.copy(apply));
            }
        }
        return result;
    }

    @Override
    public List<RedApply> findWaitingConfirmBefore(LocalDateTime threshold, int limit) {
        List<RedApply> result = new ArrayList<RedApply>();
        for (RedApply apply : byId.values()) {
            if (result.size() >= limit) {
                break;
            }
            if (apply.getState() == RedApplyState.WAITING_CONFIRM
                    && apply.getConfirmDeadline() != null
                    && apply.getConfirmDeadline().isBefore(threshold)) {
                result.add(StoreSupport.copy(apply));
            }
        }
        return result;
    }
}
