package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 红字确认单内存实现（按主键全量覆盖）。仅供单测与 example 演示。
 *
 * @author lianghang
 */
public class InMemoryRedApplyStore implements RedApplyStore {

    private final Map<String, RedApply> byId = new ConcurrentHashMap<String, RedApply>();
    private final Map<String, String> bizIndex = new ConcurrentHashMap<String, String>();

    @Override
    public void save(RedApply apply) {
        RedApply stored = StoreSupport.copy(apply);
        byId.put(stored.getRedApplyId(), stored);
        bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizRedNo()), stored.getRedApplyId());
    }

    @Override
    public void update(RedApply apply) {
        RedApply stored = StoreSupport.copy(apply);
        byId.put(stored.getRedApplyId(), stored);
        bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizRedNo()), stored.getRedApplyId());
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
}
