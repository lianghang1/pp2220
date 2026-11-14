package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;

import java.util.List;
import java.util.Set;

/**
 * 版式文件下载能力。
 *
 * <p>数电票的 XML 是法定原件，渠道可能只在开票成功后单独提供下载地址，
 * 因此把「取文件」建模为独立能力，而不是塞进开票返回值。</p>
 *
 * @author lianghang
 */
public interface InvoiceFileFetcher {

    /**
     * @param ctx   渠道调用上下文
     * @param types 期望获取的文件类型；框架至少要求 {@link InvoiceFileType#XML}
     * @return 已归一化的版式文件列表（不得返回 null）
     */
    List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types);
}
