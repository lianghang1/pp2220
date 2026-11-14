package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.AuthSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权会话持久化 SPI。
 *
 * <p>会话会过期，因此单独提供 {@link #findExpiredBefore} 让宿主做过期清理；
 * 框架不静默续期，避免拿失效的税局登录态继续开票。</p>
 *
 * @author lianghang
 */
public interface AuthSessionStore {

    void save(AuthSession session);

    boolean tryUpdate(AuthSession session, long expectedVersion);

    AuthSession findById(String sessionId);

    AuthSession findByInvoiceId(String invoiceId);

    List<AuthSession> findExpiredBefore(LocalDateTime threshold, int limit);
}
