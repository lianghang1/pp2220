package com.sfi.conflux.auth.support;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 state 缓存：单机内存实现，零外部依赖，门面未配置 stateCache 时使用。
 *
 * <p>{@link #loadAndRemove(String)} 取出即删，天然防重放。过期由条目自身时间戳判断，
 * 惰性检查（取用时判过期），不启后台清理线程——单机场景足够，集群请注入分布式实现。</p>
 *
 * @author lianghang
 */
public final class InMemoryStateCache implements StateCache {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<String, Entry>();

    @Override
    public void save(String state, Map<String, String> extra, int expireSeconds) {
        if (state == null) {
            return;
        }
        long expireAt = System.currentTimeMillis() + Math.max(1, expireSeconds) * 1000L;
        Map<String, String> snapshot = extra == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new java.util.HashMap<String, String>(extra));
        store.put(state, new Entry(snapshot, expireAt));
    }

    @Override
    public Map<String, String> loadAndRemove(String state) {
        if (state == null) {
            return null;
        }
        Entry entry = store.remove(state);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            return null;
        }
        return entry.extra;
    }

    /** 当前缓存条目数（便于测试与观测；非契约方法） */
    public int size() {
        return store.size();
    }

    private static final class Entry {
        private final Map<String, String> extra;
        private final long expireAt;

        private Entry(Map<String, String> extra, long expireAt) {
            this.extra = extra;
            this.expireAt = expireAt;
        }
    }
}
