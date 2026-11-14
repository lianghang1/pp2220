package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.AuthSessionState;
import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授权会话内存实现（CAS 版本号 + 过期扫描）。仅供单测与 example 演示。
 *
 * @author lianghang
 */
public class InMemoryAuthSessionStore implements AuthSessionStore {

    private final Map<String, AuthSession> byId = new ConcurrentHashMap<String, AuthSession>();
    private final Object lock = new Object();

    @Override
    public void save(AuthSession session) {
        synchronized (lock) {
            byId.put(session.getSessionId(), StoreSupport.copy(session));
        }
    }

    @Override
    public boolean tryUpdate(AuthSession session, long expectedVersion) {
        synchronized (lock) {
            AuthSession current = byId.get(session.getSessionId());
            if (current == null || current.getVersion() != expectedVersion) {
                return false;
            }
            byId.put(session.getSessionId(), StoreSupport.copy(session));
            return true;
        }
    }

    @Override
    public AuthSession findById(String sessionId) {
        return StoreSupport.copy(byId.get(sessionId));
    }

    @Override
    public AuthSession findByInvoiceId(String invoiceId) {
        for (AuthSession session : byId.values()) {
            if (invoiceId != null && invoiceId.equals(session.getInvoiceId())) {
                return StoreSupport.copy(session);
            }
        }
        return null;
    }

    @Override
    public List<AuthSession> findExpiredBefore(LocalDateTime threshold, int limit) {
        List<AuthSession> result = new ArrayList<AuthSession>();
        for (AuthSession session : byId.values()) {
            if (result.size() >= limit) {
                break;
            }
            if (session.getState() == AuthSessionState.PENDING
                    && session.getExpireTime() != null
                    && session.getExpireTime().isBefore(threshold)) {
                result.add(StoreSupport.copy(session));
            }
        }
        return result;
    }
}
