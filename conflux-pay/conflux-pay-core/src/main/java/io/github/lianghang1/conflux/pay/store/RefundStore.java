package io.github.lianghang1.conflux.pay.store;

import io.github.lianghang1.conflux.pay.model.Refund;

import java.time.LocalDate;
import java.util.List;

/**
 * 退款单持久化 SPI。可选能力：未注册时退款入口报 STORE_MISSING。
 *
 * <p>建议索引：{@code appId + bizRefundNo} 普通（业务单号查询）、
 * {@code paymentId} 普通索引（可退余额计算）、{@code refundId} 主键。</p>
 *
 * @author lianghang
 */
public interface RefundStore {

    /** 新增退款单。 */
    void save(Refund refund);

    /** 全量更新退款单（按 refundId 覆盖）。 */
    void update(Refund refund);

    /** 按网关退款单号查询；不存在返回 null。 */
    Refund findById(String refundId);

    /** 按业务退款单号查询（幂等键）；不存在返回 null。 */
    Refund findByBizRefundNo(String appId, String bizRefundNo);

    /** 查询支付单下的全部退款单（可退余额与退款状态聚合计算用）。 */
    List<Refund> findByPaymentId(String paymentId);

    /** 按渠道侧退款单号查询；不存在返回 null。 */
    Refund findByChannelRefundNo(String channelRefundNo);

    /**
     * 查询指定渠道、指定账单日期的全部非 CREATED 退款单（对账用）。
     * 归属日期按「退款成功时间」计。
     */
    List<Refund> findByChannelAndDate(String channelCode, String appId, LocalDate billDate);
}
