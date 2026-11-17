package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授权会话内存实现（按主键全量覆盖）。仅供单测与 example 演示。
 *
 * @author lianghang
 */
public class InMemoryAuthSessionStore implements AuthSessionStore {

    private final Map<String, AuthSession> byId = new ConcurrentHashMap<String, AuthSession>();

    @Override
    public void save(AuthSession session) {
        byId.put(session.getSessionId(), StoreSupport.copy(session));
    }

    @Override
    public void update(AuthSession session) {
        byId.put(session.getSessionId(), StoreSupport.copy(session));
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
}
