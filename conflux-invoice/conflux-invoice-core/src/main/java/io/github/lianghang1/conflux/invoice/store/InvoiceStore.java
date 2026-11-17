package io.github.lianghang1.conflux.invoice.store;

import io.github.lianghang1.conflux.invoice.model.Invoice;

import java.util.List;

/**
 * 发票单据持久化 SPI：使用方实现并注册。
 *
 * <p>框架对存储零假设——表结构、索引、事务与并发控制均由实现方自行处理。
 * 引擎只做「新增」与「按主键全量覆盖」两种写入，不校验版本号、不去重业务单号；
 * 幂等、并发一致性与补单认领交给使用方在 Store 层保证。</p>
 *
 * @author lianghang
 */
public interface InvoiceStore {

    /** 新增发票单据（引擎建单后调用一次）。 */
    void save(Invoice invoice);

    /** 按主键全量覆盖更新发票单据。 */
    void update(Invoice invoice);

    Invoice findById(String invoiceId);

    /** 按业务单号查询；不存在返回 null。 */
    Invoice findByBizOrderNo(String appId, String bizOrderNo);

    /** 按渠道侧流水号查询；不存在返回 null。 */
    Invoice findByChannelOrderNo(String channelOrderNo);

    /** 按发票号码 + 发票代码查询（红冲定位原票用）；不存在返回 null。 */
    Invoice findByInvoiceNo(String invoiceNo, String invoiceCode);
}
