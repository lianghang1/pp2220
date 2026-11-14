package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;

import java.util.List;

/**
 * 版式文件归档 SPI。
 *
 * <p>数电票 XML 是法定原件，必须与单据分开持久化、可独立核对摘要；
 * 框架在把单据置为 {@code SUCCEEDED} 前会校验至少一份 XML 原件已归档。</p>
 *
 * @author lianghang
 */
public interface InvoiceFileStore {

    /** 批量归档（同一 invoiceId + type 重复写入由实现方决定覆盖或忽略，需保证幂等）。 */
    void saveAll(List<InvoiceFile> files);

    List<InvoiceFile> findByInvoiceId(String invoiceId);

    List<InvoiceFile> findByInvoiceIdAndType(String invoiceId, InvoiceFileType type);
}
