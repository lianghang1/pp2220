package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.BillEntry;
import io.github.lianghang1.conflux.pay.api.BillSheet;
import io.github.lianghang1.conflux.pay.api.ReconReport;
import io.github.lianghang1.conflux.pay.channel.BillHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.BillEntryType;
import io.github.lianghang1.conflux.pay.model.DiscrepancyType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.ReconDiscrepancy;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.common.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对账引擎：渠道账单与本地订单双向比对，差错留痕并自动修正「渠道成功、本地未成功」的订单。
 *
 * <p>比对规则（支付与退款流水分别执行）：
 * ① 本地成功订单在渠道账单无流水 → CHANNEL_MISSING（留痕人工处理，不自动降级）；
 * ② 渠道账单流水本地无对应订单 → LOCAL_MISSING；
 * ③ 两侧均有流水但金额不等 → AMOUNT_MISMATCH；
 * ④ 渠道成功而本地未成功 → STATE_MISMATCH，并以渠道为准自动修正本地状态
 * （触发支付成功事件与下游通知）；渠道与本地均非成功视为一致。
 * 自动修正仅采用「渠道成功」方向——本地成功而渠道失败的差异涉及账单日切，
 * 交由人工裁决。</p>
 *
 * @author lianghang
 */
@Slf4j
class ReconciliationRunner {

    private final GatewayRuntime rt;

    ReconciliationRunner(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 执行指定应用、渠道、日期的对账。 */
    ReconReport reconcile(String channelCode, String appId, LocalDate billDate) {
        if (rt.reconDiscrepancyStore == null) {
            throw GatewayException.of(GatewayErrorCode.STORE_MISSING, "未注册对账差错存储，对账功能不可用");
        }
        if (rt.refundStore == null) {
            throw GatewayException.of(GatewayErrorCode.STORE_MISSING, "未注册退款单存储，对账功能不可用");
        }
        BillHandler handler = rt.registry.billHandler(channelCode);
        ChannelConfig config = rt.configProvider.getConfig(appId, channelCode);
        BillSheet bill = handler.fetchBill(appId, billDate, config);

        List<BillEntry> entries = bill.getEntries() == null ? new ArrayList<BillEntry>() : bill.getEntries();
        List<ReconDiscrepancy> discrepancies = new ArrayList<ReconDiscrepancy>();
        int matched = 0;
        matched += reconcilePayments(channelCode, appId, billDate, entries, discrepancies);
        matched += reconcileRefunds(channelCode, appId, billDate, entries, discrepancies);

        for (ReconDiscrepancy discrepancy : discrepancies) {
            discrepancy.setReconId(IdGenerator.nextReconId())
                    .setAppId(appId)
                    .setChannelCode(channelCode)
                    .setBillDate(billDate)
                    .setCreatedTime(rt.now())
                    .setUpdatedTime(rt.now());
            rt.reconDiscrepancyStore.save(discrepancy);
        }
        ReconReport report = new ReconReport()
                .setAppId(appId)
                .setChannelCode(channelCode)
                .setBillDate(billDate)
                .setChannelEntryCount(entries.size())
                .setMatchedCount(matched)
                .setDiscrepancies(discrepancies);
        log.info("对账完成: channel={}, appId={}, date={}, entries={}, matched={}, discrepancies={}",
                channelCode, appId, billDate, entries.size(), matched, discrepancies.size());
        return report;
    }

    private int reconcilePayments(String channelCode, String appId, LocalDate billDate,
                                  List<BillEntry> entries, List<ReconDiscrepancy> discrepancies) {
        Map<String, BillEntry> channelEntries = indexEntries(entries, BillEntryType.PAYMENT);
        List<Payment> localOrders = rt.paymentStore.findByChannelAndDate(channelCode, appId, billDate);
        Set<String> visited = new HashSet<String>();
        int matched = 0;
        for (Payment payment : localOrders) {
            if (!payment.getState().isPaid()) {
                continue;
            }
            visited.add(payment.getBizOrderNo());
            BillEntry entry = channelEntries.get(payment.getBizOrderNo());
            if (entry == null) {
                discrepancies.add(buildDiscrepancy(BillEntryType.PAYMENT, DiscrepancyType.CHANNEL_MISSING,
                        null, payment.getBizOrderNo(), payment.getPaymentId(), null, payment.getAmount(),
                        null, payment.getState() == null ? null : payment.getState().name(),
                        "本地已成功，渠道账单无流水"));
                continue;
            }
            if (!entry.isSuccess()) {
                discrepancies.add(buildDiscrepancy(BillEntryType.PAYMENT, DiscrepancyType.STATE_MISMATCH,
                        entry.getChannelOrderNo(), payment.getBizOrderNo(), payment.getPaymentId(),
                        entry.getAmount(), payment.getAmount(), entry.getRawState(),
                        payment.getState() == null ? null : payment.getState().name(),
                        "本地已成功，渠道流水非成功（疑似日切），留待人工裁决"));
                continue;
            }
            if (entry.getAmount() != null && !entry.getAmount().equals(payment.getAmount())) {
                discrepancies.add(buildDiscrepancy(BillEntryType.PAYMENT, DiscrepancyType.AMOUNT_MISMATCH,
                        entry.getChannelOrderNo(), payment.getBizOrderNo(), payment.getPaymentId(),
                        entry.getAmount(), payment.getAmount(), entry.getRawState(),
                        payment.getState() == null ? null : payment.getState().name(), null));
                continue;
            }
            matched++;
        }
        for (BillEntry entry : channelEntries.values()) {
            if (visited.contains(entry.getBizOrderNo())) {
                continue;
            }
            Payment local = rt.paymentStore.findByBizOrderNo(appId, entry.getBizOrderNo());
            if (local == null) {
                discrepancies.add(buildDiscrepancy(BillEntryType.PAYMENT, DiscrepancyType.LOCAL_MISSING,
                        entry.getChannelOrderNo(), entry.getBizOrderNo(), null,
                        entry.getAmount(), null, entry.getRawState(), null, null));
                continue;
            }
            visited.add(local.getBizOrderNo());
            if (entry.isSuccess() && !local.getState().isPaid()) {
                discrepancies.add(buildDiscrepancy(BillEntryType.PAYMENT, DiscrepancyType.STATE_MISMATCH,
                        entry.getChannelOrderNo(), entry.getBizOrderNo(), local.getPaymentId(),
                        entry.getAmount(), local.getAmount(), entry.getRawState(),
                        local.getState() == null ? null : local.getState().name(),
                        "渠道成功而本地未成功，已按渠道修正本地状态"));
                if (local.getChannelOrderNo() == null) {
                    local.setChannelOrderNo(entry.getChannelOrderNo());
                }
                rt.transitions.markPaymentSucceeded(local,
                        entry.getOccurTime() != null ? entry.getOccurTime() : rt.now());
            } else {
                matched++;
            }
        }
        return matched;
    }

    private int reconcileRefunds(String channelCode, String appId, LocalDate billDate,
                                 List<BillEntry> entries, List<ReconDiscrepancy> discrepancies) {
        Map<String, BillEntry> channelEntries = indexEntries(entries, BillEntryType.REFUND);
        List<Refund> localRefunds = rt.refundStore.findByChannelAndDate(channelCode, appId, billDate);
        Set<String> visited = new HashSet<String>();
        int matched = 0;
        for (Refund refund : localRefunds) {
            if (refund.getState() != io.github.lianghang1.conflux.pay.model.RefundState.SUCCEEDED) {
                continue;
            }
            visited.add(refund.getBizRefundNo());
            BillEntry entry = channelEntries.get(refund.getBizRefundNo());
            if (entry == null) {
                discrepancies.add(buildDiscrepancy(BillEntryType.REFUND, DiscrepancyType.CHANNEL_MISSING,
                        refund.getChannelRefundNo(), refund.getBizRefundNo(), refund.getRefundId(),
                        null, refund.getAmount(), null,
                        refund.getState() == null ? null : refund.getState().name(),
                        "本地退款已成功，渠道账单无流水"));
                continue;
            }
            if (!entry.isSuccess()) {
                discrepancies.add(buildDiscrepancy(BillEntryType.REFUND, DiscrepancyType.STATE_MISMATCH,
                        entry.getChannelOrderNo(), refund.getBizRefundNo(), refund.getRefundId(),
                        entry.getAmount(), refund.getAmount(), entry.getRawState(),
                        refund.getState() == null ? null : refund.getState().name(),
                        "本地退款已成功，渠道流水非成功（疑似日切），留待人工裁决"));
                continue;
            }
            if (entry.getAmount() != null && !entry.getAmount().equals(refund.getAmount())) {
                discrepancies.add(buildDiscrepancy(BillEntryType.REFUND, DiscrepancyType.AMOUNT_MISMATCH,
                        entry.getChannelOrderNo(), refund.getBizRefundNo(), refund.getRefundId(),
                        entry.getAmount(), refund.getAmount(), entry.getRawState(),
                        refund.getState() == null ? null : refund.getState().name(), null));
                continue;
            }
            matched++;
        }
        for (BillEntry entry : channelEntries.values()) {
            if (visited.contains(entry.getBizOrderNo())) {
                continue;
            }
            Refund local = rt.refundStore.findByBizRefundNo(appId, entry.getBizOrderNo());
            if (local == null) {
                discrepancies.add(buildDiscrepancy(BillEntryType.REFUND, DiscrepancyType.LOCAL_MISSING,
                        entry.getChannelOrderNo(), entry.getBizOrderNo(), null,
                        entry.getAmount(), null, entry.getRawState(), null, null));
                continue;
            }
            visited.add(local.getBizRefundNo());
            if (entry.isSuccess() && local.getState() != io.github.lianghang1.conflux.pay.model.RefundState.SUCCEEDED) {
                discrepancies.add(buildDiscrepancy(BillEntryType.REFUND, DiscrepancyType.STATE_MISMATCH,
                        entry.getChannelOrderNo(), entry.getBizOrderNo(), local.getRefundId(),
                        entry.getAmount(), local.getAmount(), entry.getRawState(),
                        local.getState() == null ? null : local.getState().name(),
                        "渠道退款成功而本地未成功，已按渠道修正本地状态"));
                if (local.getChannelRefundNo() == null) {
                    local.setChannelRefundNo(entry.getChannelOrderNo());
                }
                Payment payment = rt.paymentStore.findById(local.getPaymentId());
                rt.transitions.onRefundSucceeded(local, payment);
            } else {
                matched++;
            }
        }
        return matched;
    }

    private Map<String, BillEntry> indexEntries(List<BillEntry> entries, BillEntryType type) {
        Map<String, BillEntry> index = new HashMap<String, BillEntry>();
        for (BillEntry entry : entries) {
            if (entry.getEntryType() == type && entry.getBizOrderNo() != null) {
                index.put(entry.getBizOrderNo(), entry);
            }
        }
        return index;
    }

    private ReconDiscrepancy buildDiscrepancy(BillEntryType entryType, DiscrepancyType type,
                                              String channelOrderNo, String bizOrderNo, String localOrderNo,
                                              Long channelAmount, Long localAmount,
                                              String channelState, String localState, String remark) {
        return new ReconDiscrepancy()
                .setEntryType(entryType)
                .setDiscrepancyType(type)
                .setChannelOrderNo(channelOrderNo)
                .setBizOrderNo(bizOrderNo)
                .setLocalOrderNo(localOrderNo)
                .setChannelAmount(channelAmount)
                .setLocalAmount(localAmount)
                .setChannelState(channelState)
                .setLocalState(localState)
                .setHandled(false)
                .setRemark(remark);
    }
}
