package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.AuthSession;

/**
 * 授权会话持久化 SPI。会话会过期，框架不静默续期，避免拿失效的税局登录态继续开票；
 * 过期会话的清理由使用方自行处理。写入按主键全量覆盖，并发控制由使用方保证。
 *
 * @author lianghang
 */
public interface AuthSessionStore {

    void save(AuthSession session);

    void update(AuthSession session);

    AuthSession findById(String sessionId);

    AuthSession findByInvoiceId(String invoiceId);
}
