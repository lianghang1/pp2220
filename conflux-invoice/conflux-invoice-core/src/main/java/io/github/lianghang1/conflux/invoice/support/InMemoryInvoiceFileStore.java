package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.store.InvoiceFileStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版式文件内存归档实现：同一 (invoiceId, type) 重复写入做幂等替换。
 * 仅供单测与 example 演示；XML 法定原件需由实现方长期留存。
 *
 * @author lianghang
 */
public class InMemoryInvoiceFileStore implements InvoiceFileStore {

    private final Map<String, List<InvoiceFile>> byInvoice =
            new ConcurrentHashMap<String, List<InvoiceFile>>();
    private final Object lock = new Object();

    @Override
    public void saveAll(List<InvoiceFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        synchronized (lock) {
            for (InvoiceFile file : files) {
                if (file == null || file.getInvoiceId() == null || file.getType() == null) {
                    continue;
                }
                List<InvoiceFile> list = byInvoice.get(file.getInvoiceId());
                if (list == null) {
                    list = new ArrayList<InvoiceFile>();
                    byInvoice.put(file.getInvoiceId(), list);
                }
                List<InvoiceFile> next = new ArrayList<InvoiceFile>();
                for (InvoiceFile existing : list) {
                    if (existing.getType() != file.getType()) {
                        next.add(existing);
                    }
                }
                next.add(StoreSupport.copy(file));
                byInvoice.put(file.getInvoiceId(), next);
            }
        }
    }

    @Override
    public List<InvoiceFile> findByInvoiceId(String invoiceId) {
        List<InvoiceFile> list = byInvoice.get(invoiceId);
        List<InvoiceFile> result = new ArrayList<InvoiceFile>();
        if (list != null) {
            for (InvoiceFile file : list) {
                result.add(StoreSupport.copy(file));
            }
        }
        return result;
    }

    @Override
    public List<InvoiceFile> findByInvoiceIdAndType(String invoiceId, InvoiceFileType type) {
        List<InvoiceFile> result = new ArrayList<InvoiceFile>();
        for (InvoiceFile file : findByInvoiceId(invoiceId)) {
            if (file.getType() == type) {
                result.add(file);
            }
        }
        return result;
    }
}
