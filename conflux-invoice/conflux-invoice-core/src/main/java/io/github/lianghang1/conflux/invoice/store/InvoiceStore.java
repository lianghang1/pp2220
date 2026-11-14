package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.Invoice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票单据持久化 SPI：使用方实现并注册。
 *
 * <p><b>与支付域的关键差异：并发契约显式化。</b>支付域的 {@code PaymentStore.update(Payment)}
 * 是全量覆盖且没有 expectedState/version，回调线程与补单线程并发时后写覆盖先写，
 * 一致性只能靠实现方「自觉」；{@code findDueNotifies} 也没有认领/租约，多实例部署会重复处理。
 * 本接口把两件事写进契约：</p>
 *
 * <ul>
 *   <li><b>CAS</b>：{@link #tryUpdate} 必须按 {@code expectedVersion} 条件更新；
 *       调用方负责把待写入对象的 {@code version} 设为 {@code expectedVersion + 1}，
 *       Store 只做「比较 + 写入」不再自行递增，成功返回 true，
 *       版本不匹配返回 false 且不写入；</li>
 *   <li><b>租约</b>：{@link #claimProcessingBefore} 必须是原子的「查询 + 认领」，
 *       被认领的单据在租约期内不得被其他持有者再次领走。</li>
 * </ul>
 *
 * <p>框架对表结构零假设；建议 {@code appId + bizOrderNo} 唯一索引（幂等键）、
 * {@code invoiceId} 主键、{@code channelOrderNo} 普通索引（回调/补单定位）。</p>
 *
 * @author lianghang
 */
public interface InvoiceStore {

    /** 新增发票单据（引擎建单后调用一次）。 */
    void save(Invoice invoice);

    /**
     * 条件更新：仅当当前版本号等于 {@code expectedVersion} 时写入并把版本号 +1。
     *
     * @return 是否更新成功；false 表示并发冲突，调用方应放弃本次迁移并重试
     */
    boolean tryUpdate(Invoice invoice, long expectedVersion);

    Invoice findById(String invoiceId);

    /** 按业务单号查询（幂等键）；不存在返回 null。 */
    Invoice findByBizOrderNo(String appId, String bizOrderNo);

    /** 按渠道侧流水号查询；不存在返回 null。 */
    Invoice findByChannelOrderNo(String channelOrderNo);

    /** 按发票号码 + 发票代码查询（红冲定位原票用）；不存在返回 null。 */
    Invoice findByInvoiceNo(String invoiceNo, String invoiceCode);

    /**
     * 原子认领「仍在流转中」的超时单据（补单/轮询扫描用）。
     *
     * <p>实现方须在同一事务内完成筛选与租约写入：把 {@code claimOwner} 与
     * {@code claimExpireTime = now + leaseMillis} 写入命中的单据，
     * 并保证同一单据不会被并发的两次调用同时返回。</p>
     *
     * @return 已成功认领的单据（其 version 为认领后的最新值）
     */
    List<Invoice> claimProcessingBefore(LocalDateTime threshold, int limit,
                                        String owner, long leaseMillis);

    /** 释放租约（处理完成后调用，owner 不匹配时不释放）。 */
    boolean releaseClaim(String invoiceId, String owner);
}
