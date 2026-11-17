package io.github.lianghang1.conflux.pay.store;

import io.github.lianghang1.conflux.pay.model.Payment;

import java.time.LocalDate;
import java.util.List;

/**
 * 支付单持久化 SPI：使用方实现并注册（starter 收集 Bean / 纯 jar 手动装配）。
 *
 * <p>框架对存储零假设——表结构、索引、事务由实现方自行设计；
 * 建议为 {@code appId + bizOrderNo} 建唯一索引（幂等键）、
 * {@code paymentId} 为主键、{@code channelOrderNo} 建普通索引（回调/补单定位）。</p>
 *
 * <p>实现要求：所有方法可能被并发调用，需保证线程安全与幂等语义；
 * save 与 update 由引擎按状态机时序调用，实现方按主键覆盖即可。</p>
 *
 * @author lianghang
 */
public interface PaymentStore {

    /** 新增支付单（引擎在建单后调用一次）。 */
    void save(Payment payment);

    /** 全量更新支付单（引擎在每次状态迁移后调用，按 paymentId 覆盖）。 */
    void update(Payment payment);

    /** 按网关支付单号查询；不存在返回 null。 */
    Payment findById(String paymentId);

    /** 按业务单号查询（幂等键）；不存在返回 null。 */
    Payment findByBizOrderNo(String appId, String bizOrderNo);

    /** 按渠道侧单号查询；不存在返回 null。 */
    Payment findByChannelOrderNo(String channelOrderNo);

    /**
     * 查询指定渠道、指定账单日期的全部非 CREATED 订单（对账用）。
     * 订单归属日期按「支付成功时间」计，未成功的按更新时间计。
     */
    List<Payment> findByChannelAndDate(String channelCode, String appId, LocalDate billDate);
}
