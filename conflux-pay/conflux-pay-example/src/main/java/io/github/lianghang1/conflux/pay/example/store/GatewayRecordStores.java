package io.github.lianghang1.conflux.pay.example.store;

import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.ProfitShare;
import io.github.lianghang1.conflux.pay.model.ReconDiscrepancy;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.pay.store.NotifyLogStore;
import io.github.lianghang1.conflux.pay.store.PaymentStore;
import io.github.lianghang1.conflux.pay.store.ProfitShareStore;
import io.github.lianghang1.conflux.pay.store.ReconDiscrepancyStore;
import io.github.lianghang1.conflux.pay.store.RefundStore;
import io.github.lianghang1.conflux.pay.store.TransferStore;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 六类 Store SPI 的演示实现：共用网关记录表，模型对象以 JSON 持久化，
 * 每个存储实现各自的 SPI 接口。
 *
 * <p>生产项目建议按模型拆表（订单/退款/转账/分账/通知/差错各一张），
 * 为幂等键建唯一索引、为渠道单号与时间锚点建普通索引。</p>
 *
 * @author lianghang
 */
@Configuration
public class GatewayRecordStores {

    @Bean
    public PaymentStore paymentStore(GatewayRecordRepository repository) {
        return new JsonPaymentStore(repository, "PAYMENT");
    }

    @Bean
    public RefundStore refundStore(GatewayRecordRepository repository) {
        return new JsonRefundStore(repository, "REFUND");
    }

    @Bean
    public TransferStore transferStore(GatewayRecordRepository repository) {
        return new JsonTransferStore(repository, "TRANSFER");
    }

    @Bean
    public ProfitShareStore profitShareStore(GatewayRecordRepository repository) {
        return new JsonProfitShareStore(repository, "PROFIT_SHARE");
    }

    @Bean
    public NotifyLogStore notifyLogStore(GatewayRecordRepository repository) {
        return new JsonNotifyLogStore(repository, "NOTIFY");
    }

    @Bean
    public ReconDiscrepancyStore reconDiscrepancyStore(GatewayRecordRepository repository) {
        return new JsonReconStore(repository, "RECON");
    }

    /**
     * JSON 记录存储骨架：封装「模型 ↔ 记录表」的通用读写，子类只做接口翻译。
     */
    abstract static class AbstractJsonRecordStore<T> {

        protected final GatewayRecordRepository repository;
        protected final String recordType;
        protected final Class<T> modelType;

        AbstractJsonRecordStore(GatewayRecordRepository repository, String recordType, Class<T> modelType) {
            this.repository = repository;
            this.recordType = recordType;
            this.modelType = modelType;
        }

        protected abstract String idOf(T model);

        protected abstract String idempotentKeyOf(T model);

        protected abstract LocalDateTime anchorOf(T model);

        protected String channelOf(T model) {
            return null;
        }

        protected String channelOrderOf(T model) {
            return null;
        }

        protected void write(T model) {
            Optional<GatewayRecordEntity> existing = repository.findById(idOf(model));
            GatewayRecordEntity entity = existing.orElseGet(GatewayRecordEntity::new);
            entity.setRecordId(idOf(model));
            entity.setRecordType(recordType);
            entity.setIdempotentKey(idempotentKeyOf(model));
            entity.setChannelCode(channelOf(model));
            entity.setChannelOrderNo(channelOrderOf(model));
            entity.setAnchorTime(anchorOf(model));
            entity.setContentJson(JsonUtils.toJson(model));
            repository.save(entity);
        }

        protected T readById(String id) {
            return repository.findById(id)
                    .map(entity -> JsonUtils.fromJson(entity.getContentJson(), modelType))
                    .orElse(null);
        }

        protected T readByIdempotentKey(String idempotentKey) {
            return repository.findByRecordTypeAndIdempotentKey(recordType, idempotentKey)
                    .map(entity -> JsonUtils.fromJson(entity.getContentJson(), modelType))
                    .orElse(null);
        }

        protected T readByChannelOrderNo(String channelOrderNo) {
            return repository.findByRecordTypeAndChannelOrderNo(recordType, channelOrderNo)
                    .map(entity -> JsonUtils.fromJson(entity.getContentJson(), modelType))
                    .orElse(null);
        }

        protected List<T> readAll() {
            List<T> result = new ArrayList<T>();
            for (GatewayRecordEntity entity : repository.findByRecordType(recordType)) {
                T model = JsonUtils.fromJson(entity.getContentJson(), modelType);
                if (model != null) {
                    result.add(model);
                }
            }
            return result;
        }

        protected <R> List<R> filterByDate(List<R> models, LocalDate billDate,
                                           java.util.function.Function<R, String> channelGetter,
                                           java.util.function.Function<R, String> appGetter,
                                           java.util.function.Function<R, LocalDateTime> anchorGetter) {
            List<R> result = new ArrayList<R>();
            for (R model : models) {
                if (!billDate.equals(anchorGetter.apply(model).toLocalDate())) {
                    continue;
                }
                result.add(model);
            }
            return result;
        }
    }

    /** 支付单存储。 */
    static class JsonPaymentStore extends AbstractJsonRecordStore<Payment> implements PaymentStore {

        JsonPaymentStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, Payment.class);
        }

        @Override
        protected String idOf(Payment model) {
            return model.getPaymentId();
        }

        @Override
        protected String idempotentKeyOf(Payment model) {
            return model.getAppId() + "::" + model.getBizOrderNo();
        }

        @Override
        protected LocalDateTime anchorOf(Payment model) {
            return model.getCreatedTime();
        }

        @Override
        protected String channelOf(Payment model) {
            return model.getChannelCode();
        }

        @Override
        protected String channelOrderOf(Payment model) {
            return model.getChannelOrderNo();
        }

        @Override
        public void save(Payment payment) {
            write(payment);
        }

        @Override
        public void update(Payment payment) {
            write(payment);
        }

        @Override
        public Payment findById(String paymentId) {
            return readById(paymentId);
        }

        @Override
        public Payment findByBizOrderNo(String appId, String bizOrderNo) {
            return readByIdempotentKey(appId + "::" + bizOrderNo);
        }

        @Override
        public Payment findByChannelOrderNo(String channelOrderNo) {
            return readByChannelOrderNo(channelOrderNo);
        }

        @Override
        public List<Payment> findPayingBefore(LocalDateTime threshold, int limit) {
            List<Payment> result = new ArrayList<Payment>();
            for (Payment payment : readAll()) {
                if ((payment.getState() == PaymentState.CREATED || payment.getState() == PaymentState.PAYING)
                        && payment.getCreatedTime() != null && payment.getCreatedTime().isBefore(threshold)) {
                    result.add(payment);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public List<Payment> findByChannelAndDate(String channelCode, String appId, LocalDate billDate) {
            List<Payment> result = new ArrayList<Payment>();
            for (Payment payment : readAll()) {
                if (!channelCode.equals(payment.getChannelCode()) || !appId.equals(payment.getAppId())) {
                    continue;
                }
                LocalDateTime anchor = payment.getSucceedTime() != null
                        ? payment.getSucceedTime() : payment.getUpdatedTime();
                if (anchor != null && billDate.equals(anchor.toLocalDate())) {
                    result.add(payment);
                }
            }
            return result;
        }
    }

    /** 退款单存储。 */
    static class JsonRefundStore extends AbstractJsonRecordStore<Refund> implements RefundStore {

        JsonRefundStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, Refund.class);
        }

        @Override
        protected String idOf(Refund model) {
            return model.getRefundId();
        }

        @Override
        protected String idempotentKeyOf(Refund model) {
            return model.getAppId() + "::" + model.getBizRefundNo();
        }

        @Override
        protected LocalDateTime anchorOf(Refund model) {
            return model.getCreatedTime();
        }

        @Override
        protected String channelOf(Refund model) {
            return model.getChannelCode();
        }

        @Override
        protected String channelOrderOf(Refund model) {
            return model.getChannelRefundNo();
        }

        @Override
        public void save(Refund refund) {
            write(refund);
        }

        @Override
        public void update(Refund refund) {
            write(refund);
        }

        @Override
        public Refund findById(String refundId) {
            return readById(refundId);
        }

        @Override
        public Refund findByBizRefundNo(String appId, String bizRefundNo) {
            return readByIdempotentKey(appId + "::" + bizRefundNo);
        }

        @Override
        public List<Refund> findByPaymentId(String paymentId) {
            List<Refund> result = new ArrayList<Refund>();
            for (Refund refund : readAll()) {
                if (refund.getPaymentId().equals(paymentId)) {
                    result.add(refund);
                }
            }
            return result;
        }

        @Override
        public Refund findByChannelRefundNo(String channelRefundNo) {
            return readByChannelOrderNo(channelRefundNo);
        }

        @Override
        public List<Refund> findByChannelAndDate(String channelCode, String appId, LocalDate billDate) {
            List<Refund> result = new ArrayList<Refund>();
            for (Refund refund : readAll()) {
                if (!channelCode.equals(refund.getChannelCode()) || !appId.equals(refund.getAppId())) {
                    continue;
                }
                LocalDateTime anchor = refund.getSucceedTime() != null
                        ? refund.getSucceedTime() : refund.getUpdatedTime();
                if (anchor != null && billDate.equals(anchor.toLocalDate())
                        && refund.getState() != RefundState.CREATED) {
                    result.add(refund);
                }
            }
            return result;
        }
    }

    /** 转账单存储。 */
    static class JsonTransferStore extends AbstractJsonRecordStore<Transfer> implements TransferStore {

        JsonTransferStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, Transfer.class);
        }

        @Override
        protected String idOf(Transfer model) {
            return model.getTransferId();
        }

        @Override
        protected String idempotentKeyOf(Transfer model) {
            return model.getAppId() + "::" + model.getBizTransferNo();
        }

        @Override
        protected LocalDateTime anchorOf(Transfer model) {
            return model.getCreatedTime();
        }

        @Override
        protected String channelOf(Transfer model) {
            return model.getChannelCode();
        }

        @Override
        protected String channelOrderOf(Transfer model) {
            return model.getChannelTransferNo();
        }

        @Override
        public void save(Transfer transfer) {
            write(transfer);
        }

        @Override
        public void update(Transfer transfer) {
            write(transfer);
        }

        @Override
        public Transfer findById(String transferId) {
            return readById(transferId);
        }

        @Override
        public Transfer findByBizTransferNo(String appId, String bizTransferNo) {
            return readByIdempotentKey(appId + "::" + bizTransferNo);
        }
    }

    /** 分账单存储。 */
    static class JsonProfitShareStore extends AbstractJsonRecordStore<ProfitShare> implements ProfitShareStore {

        JsonProfitShareStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, ProfitShare.class);
        }

        @Override
        protected String idOf(ProfitShare model) {
            return model.getShareId();
        }

        @Override
        protected String idempotentKeyOf(ProfitShare model) {
            return model.getAppId() + "::" + model.getBizShareNo();
        }

        @Override
        protected LocalDateTime anchorOf(ProfitShare model) {
            return model.getCreatedTime();
        }

        @Override
        protected String channelOf(ProfitShare model) {
            return model.getChannelCode();
        }

        @Override
        protected String channelOrderOf(ProfitShare model) {
            return model.getChannelShareNo();
        }

        @Override
        public void save(ProfitShare share) {
            write(share);
        }

        @Override
        public void update(ProfitShare share) {
            write(share);
        }

        @Override
        public ProfitShare findById(String shareId) {
            return readById(shareId);
        }

        @Override
        public ProfitShare findByBizShareNo(String appId, String bizShareNo) {
            return readByIdempotentKey(appId + "::" + bizShareNo);
        }
    }

    /** 下游通知记录存储。 */
    static class JsonNotifyLogStore extends AbstractJsonRecordStore<NotifyLog> implements NotifyLogStore {

        JsonNotifyLogStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, NotifyLog.class);
        }

        @Override
        protected String idOf(NotifyLog model) {
            return model.getNotifyId();
        }

        @Override
        protected String idempotentKeyOf(NotifyLog model) {
            return model.getNotifyId();
        }

        @Override
        protected LocalDateTime anchorOf(NotifyLog model) {
            return model.getNextNotifyTime();
        }

        @Override
        public void save(NotifyLog notifyLog) {
            write(notifyLog);
        }

        @Override
        public void update(NotifyLog notifyLog) {
            write(notifyLog);
        }

        @Override
        public List<NotifyLog> findDueNotifies(LocalDateTime threshold, int limit) {
            List<NotifyLog> result = new ArrayList<NotifyLog>();
            for (NotifyLog notifyLog : readAll()) {
                if (Boolean.TRUE.equals(notifyLog.getCompleted())) {
                    continue;
                }
                if (notifyLog.getNextNotifyTime() != null && !notifyLog.getNextNotifyTime().isAfter(threshold)) {
                    result.add(notifyLog);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public List<NotifyLog> findByOrderId(String orderId) {
            List<NotifyLog> result = new ArrayList<NotifyLog>();
            for (NotifyLog notifyLog : readAll()) {
                if (notifyLog.getOrderId().equals(orderId)) {
                    result.add(notifyLog);
                }
            }
            return result;
        }
    }

    /** 对账差错存储。 */
    static class JsonReconStore extends AbstractJsonRecordStore<ReconDiscrepancy> implements ReconDiscrepancyStore {

        JsonReconStore(GatewayRecordRepository repository, String recordType) {
            super(repository, recordType, ReconDiscrepancy.class);
        }

        @Override
        protected String idOf(ReconDiscrepancy model) {
            return model.getReconId();
        }

        @Override
        protected String idempotentKeyOf(ReconDiscrepancy model) {
            return model.getReconId();
        }

        @Override
        protected LocalDateTime anchorOf(ReconDiscrepancy model) {
            return model.getCreatedTime();
        }

        @Override
        protected String channelOf(ReconDiscrepancy model) {
            return model.getChannelCode();
        }

        @Override
        public void save(ReconDiscrepancy discrepancy) {
            write(discrepancy);
        }

        @Override
        public void update(ReconDiscrepancy discrepancy) {
            write(discrepancy);
        }

        @Override
        public List<ReconDiscrepancy> findByDate(String channelCode, String appId, LocalDate billDate) {
            List<ReconDiscrepancy> result = new ArrayList<ReconDiscrepancy>();
            for (ReconDiscrepancy discrepancy : readAll()) {
                if (discrepancy.getChannelCode().equals(channelCode)
                        && discrepancy.getAppId().equals(appId)
                        && discrepancy.getBillDate().equals(billDate)) {
                    result.add(discrepancy);
                }
            }
            return result;
        }
    }
}
