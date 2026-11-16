package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发票单据内存实现：演示 {@link InvoiceStore} 的 CAS 与租约契约。
 *
 * <p><b>仅供单测、单机演示与 example 宿主使用</b>；生产请实现自己的持久化
 * （数据库 + 唯一索引 + 条件更新），框架对表结构零假设。</p>
 *
 * @author lianghang
 */
public class InMemoryInvoiceStore implements InvoiceStore {

    private final Map<String, Invoice> byId = new ConcurrentHashMap<String, Invoice>();
    private final Map<String, String> bizIndex = new ConcurrentHashMap<String, String>();
    private final Map<String, String> channelIndex = new ConcurrentHashMap<String, String>();
    private final Object lock = new Object();

    @Override
    public void save(Invoice invoice) {
        synchronized (lock) {
            String bizKey = StoreSupport.key(invoice.getAppId(), invoice.getBizOrderNo());
            if (bizIndex.containsKey(bizKey)) {
                throw new GatewayException(InvoiceErrorCode.DUPLICATE_BIZ_ORDER,
                        "业务单号已存在: {}", invoice.getBizOrderNo());
            }
            Invoice stored = StoreSupport.copy(invoice);
            byId.put(stored.getInvoiceId(), stored);
            bizIndex.put(bizKey, stored.getInvoiceId());
            if (stored.getChannelOrderNo() != null) {
                channelIndex.put(stored.getChannelOrderNo(), stored.getInvoiceId());
            }
        }
    }

    @Override
    public boolean tryUpdate(Invoice invoice, long expectedVersion) {
        synchronized (lock) {
            Invoice current = byId.get(invoice.getInvoiceId());
            if (current == null || current.getVersion() != expectedVersion) {
                return false;
            }
            Invoice stored = StoreSupport.copy(invoice);
            byId.put(stored.getInvoiceId(), stored);
            bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizOrderNo()),
                    stored.getInvoiceId());
            if (stored.getChannelOrderNo() != null) {
                channelIndex.put(stored.getChannelOrderNo(), stored.getInvoiceId());
            }
            return true;
        }
    }

    @Override
    public Invoice findById(String invoiceId) {
        return StoreSupport.copy(byId.get(invoiceId));
    }

    @Override
    public Invoice findByBizOrderNo(String appId, String bizOrderNo) {
        String id = bizIndex.get(StoreSupport.key(appId, bizOrderNo));
        return id == null ? null : findById(id);
    }

    @Override
    public Invoice findByChannelOrderNo(String channelOrderNo) {
        String id = channelIndex.get(channelOrderNo);
        return id == null ? null : findById(id);
    }

    @Override
    public Invoice findByInvoiceNo(String invoiceNo, String invoiceCode) {
        if (invoiceNo == null) {
            return null;
        }
        for (Invoice invoice : byId.values()) {
            if (invoiceNo.equals(invoice.getInvoiceNo())
                    && (invoiceCode == null || invoiceCode.equals(invoice.getInvoiceCode()))) {
                return StoreSupport.copy(invoice);
            }
        }
        return null;
    }

    @Override
    public List<Invoice> claimProcessingBefore(LocalDateTime threshold, int limit,
                                               String owner, long leaseMillis) {
        List<Invoice> claimed = new ArrayList<Invoice>();
        if (limit <= 0) {
            return claimed;
        }
        synchronized (lock) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime leaseUntil = now.plusNanos(leaseMillis * 1000000L);
            for (Invoice invoice : byId.values()) {
                if (claimed.size() >= limit) {
                    break;
                }
                if (invoice.getState() == null || !invoice.getState().isInFlight()) {
                    continue;
                }
                if (invoice.getUpdatedTime() != null
                        && !invoice.getUpdatedTime().isBefore(threshold)) {
                    continue;
                }
                // 他人持有的未过期租约，跳过
                if (invoice.getClaimExpireTime() != null
                        && invoice.getClaimExpireTime().isAfter(now)
                        && invoice.getClaimOwner() != null
                        && !invoice.getClaimOwner().equals(owner)) {
                    continue;
                }
                Invoice updated = StoreSupport.copy(invoice);
                long expected = updated.getVersion();
                updated.setClaimOwner(owner)
                        .setClaimExpireTime(leaseUntil)
                        .setVersion(expected + 1);
                byId.put(updated.getInvoiceId(), updated);
                claimed.add(StoreSupport.copy(updated));
            }
        }
        return claimed;
    }

    @Override
    public boolean releaseClaim(String invoiceId, String owner) {
        synchronized (lock) {
            Invoice current = byId.get(invoiceId);
            if (current == null || current.getClaimOwner() == null
                    || !current.getClaimOwner().equals(owner)) {
                return false;
            }
            Invoice updated = StoreSupport.copy(current);
            long expected = updated.getVersion();
            updated.setClaimOwner(null).setClaimExpireTime(null).setVersion(expected + 1);
            byId.put(updated.getInvoiceId(), updated);
            return true;
        }
    }
}
