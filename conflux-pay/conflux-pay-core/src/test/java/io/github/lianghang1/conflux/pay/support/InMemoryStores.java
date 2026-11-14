package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.ProfitShare;
import io.github.lianghang1.conflux.pay.model.ReconDiscrepancy;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.pay.store.NotifyLogStore;
import io.github.lianghang1.conflux.pay.store.PaymentStore;
import io.github.lianghang1.conflux.pay.store.ProfitShareStore;
import io.github.lianghang1.conflux.pay.store.ReconDiscrepancyStore;
import io.github.lianghang1.conflux.pay.store.RefundStore;
import io.github.lianghang1.conflux.pay.store.TransferStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 测试用内存 Store 实现：ConcurrentHashMap 承载，满足 SPI 契约的最简语义。
 *
 * @author lianghang
 */
public final class InMemoryStores {

    private InMemoryStores() {
    }

    public static class PaymentStoreImpl implements PaymentStore {

        private final Map<String, Payment> data = new ConcurrentHashMap<String, Payment>();

        @Override
        public void save(Payment payment) {
            data.put(payment.getPaymentId(), payment);
        }

        @Override
        public void update(Payment payment) {
            if (!data.containsKey(payment.getPaymentId())) {
                throw new IllegalStateException("更新对象不存在: " + payment.getPaymentId());
            }
            data.put(payment.getPaymentId(), payment);
        }

        @Override
        public Payment findById(String paymentId) {
            return data.get(paymentId);
        }

        @Override
        public Payment findByBizOrderNo(String appId, String bizOrderNo) {
            for (Payment payment : data.values()) {
                if (payment.getAppId().equals(appId) && payment.getBizOrderNo().equals(bizOrderNo)) {
                    return payment;
                }
            }
            return null;
        }

        @Override
        public Payment findByChannelOrderNo(String channelOrderNo) {
            for (Payment payment : data.values()) {
                if (channelOrderNo.equals(payment.getChannelOrderNo())) {
                    return payment;
                }
            }
            return null;
        }

        @Override
        public List<Payment> findPayingBefore(LocalDateTime threshold, int limit) {
            return data.values().stream()
                    .filter(p -> p.getState() == PaymentState.CREATED || p.getState() == PaymentState.PAYING)
                    .filter(p -> p.getCreatedTime() != null && p.getCreatedTime().isBefore(threshold))
                    .sorted(Comparator.comparing(Payment::getCreatedTime))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public List<Payment> findByChannelAndDate(String channelCode, String appId, LocalDate billDate) {
            List<Payment> result = new ArrayList<Payment>();
            for (Payment payment : data.values()) {
                if (!channelCode.equals(payment.getChannelCode()) || !appId.equals(payment.getAppId())) {
                    continue;
                }
                LocalDateTime anchor = payment.getSucceedTime() != null ? payment.getSucceedTime() : payment.getUpdatedTime();
                if (anchor != null && billDate.equals(anchor.toLocalDate())) {
                    result.add(payment);
                }
            }
            return result;
        }

        public List<Payment> all() {
            return new ArrayList<Payment>(data.values());
        }
    }

    public static class RefundStoreImpl implements RefundStore {

        private final Map<String, Refund> data = new ConcurrentHashMap<String, Refund>();

        @Override
        public void save(Refund refund) {
            data.put(refund.getRefundId(), refund);
        }

        @Override
        public void update(Refund refund) {
            data.put(refund.getRefundId(), refund);
        }

        @Override
        public Refund findById(String refundId) {
            return data.get(refundId);
        }

        @Override
        public Refund findByBizRefundNo(String appId, String bizRefundNo) {
            for (Refund refund : data.values()) {
                if (refund.getAppId().equals(appId) && refund.getBizRefundNo().equals(bizRefundNo)) {
                    return refund;
                }
            }
            return null;
        }

        @Override
        public List<Refund> findByPaymentId(String paymentId) {
            List<Refund> result = new ArrayList<Refund>();
            for (Refund refund : data.values()) {
                if (refund.getPaymentId().equals(paymentId)) {
                    result.add(refund);
                }
            }
            return result;
        }

        @Override
        public Refund findByChannelRefundNo(String channelRefundNo) {
            for (Refund refund : data.values()) {
                if (channelRefundNo.equals(refund.getChannelRefundNo())) {
                    return refund;
                }
            }
            return null;
        }

        @Override
        public List<Refund> findByChannelAndDate(String channelCode, String appId, LocalDate billDate) {
            List<Refund> result = new ArrayList<Refund>();
            for (Refund refund : data.values()) {
                if (!channelCode.equals(refund.getChannelCode()) || !appId.equals(refund.getAppId())) {
                    continue;
                }
                LocalDateTime anchor = refund.getSucceedTime() != null ? refund.getSucceedTime() : refund.getUpdatedTime();
                if (anchor != null && billDate.equals(anchor.toLocalDate())) {
                    result.add(refund);
                }
            }
            return result;
        }

        public List<Refund> all() {
            return new ArrayList<Refund>(data.values());
        }
    }

    public static class TransferStoreImpl implements TransferStore {

        private final Map<String, Transfer> data = new ConcurrentHashMap<String, Transfer>();

        @Override
        public void save(Transfer transfer) {
            data.put(transfer.getTransferId(), transfer);
        }

        @Override
        public void update(Transfer transfer) {
            data.put(transfer.getTransferId(), transfer);
        }

        @Override
        public Transfer findById(String transferId) {
            return data.get(transferId);
        }

        @Override
        public Transfer findByBizTransferNo(String appId, String bizTransferNo) {
            for (Transfer transfer : data.values()) {
                if (transfer.getAppId().equals(appId) && transfer.getBizTransferNo().equals(bizTransferNo)) {
                    return transfer;
                }
            }
            return null;
        }
    }

    public static class ProfitShareStoreImpl implements ProfitShareStore {

        private final Map<String, ProfitShare> data = new ConcurrentHashMap<String, ProfitShare>();

        @Override
        public void save(ProfitShare share) {
            data.put(share.getShareId(), share);
        }

        @Override
        public void update(ProfitShare share) {
            data.put(share.getShareId(), share);
        }

        @Override
        public ProfitShare findById(String shareId) {
            return data.get(shareId);
        }

        @Override
        public ProfitShare findByBizShareNo(String appId, String bizShareNo) {
            for (ProfitShare share : data.values()) {
                if (share.getAppId().equals(appId) && share.getBizShareNo().equals(bizShareNo)) {
                    return share;
                }
            }
            return null;
        }
    }

    public static class NotifyLogStoreImpl implements NotifyLogStore {

        private final Map<String, NotifyLog> data = new ConcurrentHashMap<String, NotifyLog>();

        @Override
        public void save(NotifyLog notifyLog) {
            data.put(notifyLog.getNotifyId(), notifyLog);
        }

        @Override
        public void update(NotifyLog notifyLog) {
            data.put(notifyLog.getNotifyId(), notifyLog);
        }

        @Override
        public List<NotifyLog> findDueNotifies(LocalDateTime threshold, int limit) {
            return data.values().stream()
                    .filter(n -> !Boolean.TRUE.equals(n.getCompleted()))
                    .filter(n -> n.getNextNotifyTime() != null && !n.getNextNotifyTime().isAfter(threshold))
                    .sorted(Comparator.comparing(NotifyLog::getNextNotifyTime))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public List<NotifyLog> findByOrderId(String orderId) {
            List<NotifyLog> result = new ArrayList<NotifyLog>();
            for (NotifyLog notifyLog : data.values()) {
                if (notifyLog.getOrderId().equals(orderId)) {
                    result.add(notifyLog);
                }
            }
            return result;
        }

        public List<NotifyLog> all() {
            return new ArrayList<NotifyLog>(data.values());
        }
    }

    public static class ReconDiscrepancyStoreImpl implements ReconDiscrepancyStore {

        private final Map<String, ReconDiscrepancy> data = new ConcurrentHashMap<String, ReconDiscrepancy>();

        @Override
        public void save(ReconDiscrepancy discrepancy) {
            data.put(discrepancy.getReconId(), discrepancy);
        }

        @Override
        public void update(ReconDiscrepancy discrepancy) {
            data.put(discrepancy.getReconId(), discrepancy);
        }

        @Override
        public List<ReconDiscrepancy> findByDate(String channelCode, String appId, LocalDate billDate) {
            List<ReconDiscrepancy> result = new ArrayList<ReconDiscrepancy>();
            for (ReconDiscrepancy discrepancy : data.values()) {
                if (discrepancy.getChannelCode().equals(channelCode)
                        && discrepancy.getAppId().equals(appId)
                        && discrepancy.getBillDate().equals(billDate)) {
                    result.add(discrepancy);
                }
            }
            return result;
        }

        public List<ReconDiscrepancy> all() {
            return new ArrayList<ReconDiscrepancy>(data.values());
        }
    }
}
