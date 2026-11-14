package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.event.PaymentEvent;
import io.github.lianghang1.paygateway.event.PaymentEventListener;
import io.github.lianghang1.paygateway.model.BizOrderType;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.ProfitShare;
import io.github.lianghang1.paygateway.model.Refund;
import io.github.lianghang1.paygateway.model.Transfer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 订单事件发布器：遍历监听器广播事件，单监听器异常被隔离记录。
 *
 * <p>同步发布（在订单状态迁移的调用线程内执行）；监听方耗时的处理建议自行异步。
 * 线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class EventPublisher {

    private final List<PaymentEventListener> listeners;

    /** @param listeners 不可变快照（构建期固定） */
    public EventPublisher(List<PaymentEventListener> listeners) {
        this.listeners = listeners;
    }

    /** 发布支付单事件。 */
    public void publishPayment(PaymentEvent.EventType type, Payment payment) {
        publish(type, BizOrderType.PAYMENT, payment.getPaymentId(),
                payment.getBizOrderNo(), payment.getAppId(), payment);
    }

    /** 发布退款单事件。 */
    public void publishRefund(PaymentEvent.EventType type, Refund refund) {
        publish(type, BizOrderType.REFUND, refund.getRefundId(),
                refund.getBizRefundNo(), refund.getAppId(), refund);
    }

    /** 发布转账单事件。 */
    public void publishTransfer(PaymentEvent.EventType type, Transfer transfer) {
        publish(type, BizOrderType.TRANSFER, transfer.getTransferId(),
                transfer.getBizTransferNo(), transfer.getAppId(), transfer);
    }

    /** 发布分账单事件。 */
    public void publishProfitShare(PaymentEvent.EventType type, ProfitShare share) {
        publish(type, BizOrderType.PROFIT_SHARE, share.getShareId(),
                share.getBizShareNo(), share.getAppId(), share);
    }

    private void publish(PaymentEvent.EventType type, BizOrderType orderType,
                         String orderId, String bizOrderNo, String appId, Object subject) {
        if (listeners.isEmpty()) {
            return;
        }
        PaymentEvent event = new PaymentEvent()
                .setType(type)
                .setAppId(appId)
                .setOrderType(orderType)
                .setOrderId(orderId)
                .setBizOrderNo(bizOrderNo)
                .setOccurredAt(java.time.LocalDateTime.now())
                .setSubject(subject);
        for (PaymentEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("订单事件监听器执行失败: listener={}, event={}/{}",
                        listener.getClass().getName(), orderType, type, e);
            }
        }
    }
}
