package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发票单据内存实现：按主键全量覆盖写入，仅供单测、单机演示与 example 宿主使用。
 *
 * @author lianghang
 */
public class InMemoryInvoiceStore implements InvoiceStore {

    private final Map<String, Invoice> byId = new ConcurrentHashMap<String, Invoice>();
    private final Map<String, String> bizIndex = new ConcurrentHashMap<String, String>();
    private final Map<String, String> channelIndex = new ConcurrentHashMap<String, String>();

    @Override
    public void save(Invoice invoice) {
        Invoice stored = StoreSupport.copy(invoice);
        byId.put(stored.getInvoiceId(), stored);
        bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizOrderNo()), stored.getInvoiceId());
        if (stored.getChannelOrderNo() != null) {
            channelIndex.put(stored.getChannelOrderNo(), stored.getInvoiceId());
        }
    }

    @Override
    public void update(Invoice invoice) {
        Invoice stored = StoreSupport.copy(invoice);
        byId.put(stored.getInvoiceId(), stored);
        bizIndex.put(StoreSupport.key(stored.getAppId(), stored.getBizOrderNo()), stored.getInvoiceId());
        if (stored.getChannelOrderNo() != null) {
            channelIndex.put(stored.getChannelOrderNo(), stored.getInvoiceId());
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
}
