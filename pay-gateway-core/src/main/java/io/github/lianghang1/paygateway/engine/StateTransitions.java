package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.api.NotifyMessage;
import io.github.lianghang1.paygateway.event.PaymentEvent;
import io.github.lianghang1.paygateway.model.BizOrderType;
import io.github.lianghang1.paygateway.model.NotifyLog;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.ProfitShare;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import io.github.lianghang1.paygateway.model.Refund;
import io.github.lianghang1.paygateway.model.RefundState;
import io.github.lianghang1.paygateway.model.Transfer;
import io.github.lianghang1.paygateway.model.TransferState;
import io.github.lianghang1.paygateway.util.IdGenerator;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 状态迁移执行器：四类单据终态迁移的唯一入口，负责「更新台账 → 发布事件 →
 * 入队下游通知」的固定次序。
 *
 * <p>幂等保护：迁移前校验当前状态，重复迁移静默跳过（渠道回调可能重复到达）。
 * 全部方法线程安全由调用方保证——同一订单的迁移由引擎串行触发（回调线程、
 * 补单线程间可能并发，Store 实现方的按主键覆盖写入保证最终一致）。</p>
 *
 * @author lianghang
 */
@Slf4j
class StateTransitions {

    private final GatewayRuntime rt;

    StateTransitions(GatewayRuntime rt) {
        this.rt = rt;
    }

    /**
     * 支付成功迁移：更新台账、发布事件、入队通知。
     * @return 是否实际发生迁移（false 表示订单已是成功态）
     */
    boolean markPaymentSucceeded(Payment payment, LocalDateTime succeedTime) {
        if (payment.getState() == PaymentState.SUCCEEDED) {
            return false;
        }
        payment.setState(PaymentState.SUCCEEDED);
        payment.setSucceedTime(succeedTime != null ? succeedTime : rt.now());
        payment.setUpdatedTime(rt.now());
        rt.paymentStore.update(payment);
        rt.publisher.publishPayment(PaymentEvent.EventType.PAYMENT_SUCCEEDED, payment);
        enqueueNotify(payment);
        return true;
    }

    /** 支付失败迁移：记录错误信息、更新台账、发布事件。 */
    void markPaymentFailed(Payment payment, String errorCode, String errorMsg) {
        if (payment.getState().isTerminal() && payment.getState() != PaymentState.FAILED) {
            log.warn("忽略对终态订单的失败迁移: paymentId={}, state={}", payment.getPaymentId(), payment.getState());
            return;
        }
        payment.setState(PaymentState.FAILED);
        payment.setErrorCode(errorCode);
        payment.setErrorMsg(errorMsg);
        payment.setUpdatedTime(rt.now());
        rt.paymentStore.update(payment);
        rt.publisher.publishPayment(PaymentEvent.EventType.PAYMENT_FAILED, payment);
    }

    /** 支付单关闭迁移。 */
    void markPaymentClosed(Payment payment) {
        if (payment.getState() == PaymentState.CLOSED) {
            return;
        }
        payment.setState(PaymentState.CLOSED);
        payment.setUpdatedTime(rt.now());
        rt.paymentStore.update(payment);
        rt.publisher.publishPayment(PaymentEvent.EventType.PAYMENT_CLOSED, payment);
        enqueueNotify(payment);
    }

    /** 退款成功迁移：更新退款单并联动支付单的退款聚合状态。 */
    void onRefundSucceeded(Refund refund, Payment payment) {
        if (refund.getState() == RefundState.SUCCEEDED) {
            return;
        }
        refund.setState(RefundState.SUCCEEDED);
        refund.setSucceedTime(rt.now());
        refund.setUpdatedTime(rt.now());
        rt.refundStore.update(refund);
        rt.publisher.publishRefund(PaymentEvent.EventType.REFUND_SUCCEEDED, refund);
        if (payment != null) {
            recomputePaymentRefundState(payment);
        }
        enqueueNotify(refund);
    }

    /** 退款失败迁移。 */
    void onRefundFailed(Refund refund) {
        if (refund.getState().isTerminal()) {
            return;
        }
        refund.setState(RefundState.FAILED);
        refund.setUpdatedTime(rt.now());
        rt.refundStore.update(refund);
        rt.publisher.publishRefund(PaymentEvent.EventType.REFUND_FAILED, refund);
        enqueueNotify(refund);
    }

    /** 依据全部退款单重算支付单的退款聚合状态（PART_REFUNDED / REFUNDED）。 */
    void recomputePaymentRefundState(Payment payment) {
        List<Refund> refunds = rt.refundStore.findByPaymentId(payment.getPaymentId());
        long refunded = 0L;
        for (Refund refund : refunds) {
            if (refund.getState() == RefundState.SUCCEEDED && refund.getAmount() != null) {
                refunded += refund.getAmount();
            }
        }
        PaymentState target;
        if (payment.getAmount() != null && refunded >= payment.getAmount()) {
            target = PaymentState.REFUNDED;
        } else if (refunded > 0) {
            target = PaymentState.PART_REFUNDED;
        } else {
            target = PaymentState.SUCCEEDED;
        }
        if (payment.getState() != target) {
            payment.setState(target);
            payment.setUpdatedTime(rt.now());
            rt.paymentStore.update(payment);
        }
    }

    /** 转账终态迁移。 */
    void onTransferFinished(Transfer transfer, TransferState state, LocalDateTime succeedTime) {
        if (transfer.getState().isTerminal()) {
            return;
        }
        transfer.setState(state);
        if (state == TransferState.SUCCEEDED) {
            transfer.setSucceedTime(succeedTime != null ? succeedTime : rt.now());
        }
        transfer.setUpdatedTime(rt.now());
        rt.transferStore.update(transfer);
        rt.publisher.publishTransfer(state == TransferState.SUCCEEDED
                ? PaymentEvent.EventType.TRANSFER_SUCCEEDED
                : PaymentEvent.EventType.TRANSFER_FAILED, transfer);
        enqueueNotify(transfer);
    }

    /** 分账终态迁移。 */
    void onProfitShareFinished(ProfitShare share, ProfitShareState state, LocalDateTime finishTime) {
        if (share.getState().isTerminal()) {
            return;
        }
        share.setState(state);
        if (state == ProfitShareState.FINISHED) {
            share.setFinishTime(finishTime != null ? finishTime : rt.now());
        }
        share.setUpdatedTime(rt.now());
        rt.profitShareStore.update(share);
        rt.publisher.publishProfitShare(PaymentEvent.EventType.PROFIT_SHARE_FINISHED, share);
        enqueueNotify(share);
    }

    private void enqueueNotify(Object record) {
        if (rt.notifier == null || rt.notifyLogStore == null) {
            return;
        }
        NotifyMessage message;
        String notifyUrl;
        String appId;
        String orderId;
        String bizOrderNo;
        BizOrderType orderType;
        if (record instanceof Payment) {
            Payment payment = (Payment) record;
            notifyUrl = payment.getNotifyUrl();
            appId = payment.getAppId();
            orderId = payment.getPaymentId();
            bizOrderNo = payment.getBizOrderNo();
            orderType = BizOrderType.PAYMENT;
            message = new NotifyMessage()
                    .setState(payment.getState() == null ? null : payment.getState().name())
                    .setAmount(payment.getAmount())
                    .setCurrency(payment.getCurrency())
                    .setChannelCode(payment.getChannelCode())
                    .setChannelOrderNo(payment.getChannelOrderNo())
                    .setSucceedTime(payment.getSucceedTime())
                    .setErrorCode(payment.getErrorCode())
                    .setErrorMsg(payment.getErrorMsg());
        } else if (record instanceof Refund) {
            Refund refund = (Refund) record;
            notifyUrl = refund.getNotifyUrl();
            appId = refund.getAppId();
            orderId = refund.getRefundId();
            bizOrderNo = refund.getBizRefundNo();
            orderType = BizOrderType.REFUND;
            message = new NotifyMessage()
                    .setState(refund.getState() == null ? null : refund.getState().name())
                    .setAmount(refund.getAmount())
                    .setCurrency(refund.getCurrency())
                    .setChannelCode(refund.getChannelCode())
                    .setChannelOrderNo(refund.getChannelRefundNo())
                    .setSucceedTime(refund.getSucceedTime())
                    .setErrorCode(refund.getErrorCode())
                    .setErrorMsg(refund.getErrorMsg());
        } else if (record instanceof Transfer) {
            Transfer transfer = (Transfer) record;
            notifyUrl = transfer.getNotifyUrl();
            appId = transfer.getAppId();
            orderId = transfer.getTransferId();
            bizOrderNo = transfer.getBizTransferNo();
            orderType = BizOrderType.TRANSFER;
            message = new NotifyMessage()
                    .setState(transfer.getState() == null ? null : transfer.getState().name())
                    .setAmount(transfer.getAmount())
                    .setCurrency(transfer.getCurrency())
                    .setChannelCode(transfer.getChannelCode())
                    .setChannelOrderNo(transfer.getChannelTransferNo())
                    .setSucceedTime(transfer.getSucceedTime())
                    .setErrorCode(transfer.getErrorCode())
                    .setErrorMsg(transfer.getErrorMsg());
        } else if (record instanceof ProfitShare) {
            ProfitShare share = (ProfitShare) record;
            notifyUrl = share.getNotifyUrl();
            appId = share.getAppId();
            orderId = share.getShareId();
            bizOrderNo = share.getBizShareNo();
            orderType = BizOrderType.PROFIT_SHARE;
            message = new NotifyMessage()
                    .setState(share.getState() == null ? null : share.getState().name())
                    .setAmount(share.getAmount())
                    .setCurrency(null)
                    .setChannelCode(share.getChannelCode())
                    .setChannelOrderNo(share.getChannelShareNo())
                    .setSucceedTime(share.getFinishTime())
                    .setErrorCode(share.getErrorCode())
                    .setErrorMsg(share.getErrorMsg());
        } else {
            return;
        }
        if (StringUtils.isBlank(notifyUrl)) {
            return;
        }
        message.setAppId(appId).setOrderType(orderType).setOrderId(orderId).setBizOrderNo(bizOrderNo);
        LocalDateTime now = rt.now();
        NotifyLog notifyLog = new NotifyLog()
                .setNotifyId(IdGenerator.nextNotifyId())
                .setAppId(appId)
                .setOrderType(orderType)
                .setOrderId(orderId)
                .setBizOrderNo(bizOrderNo)
                .setNotifyUrl(notifyUrl)
                .setNotifyTimes(0)
                .setNextNotifyTime(now)
                .setSuccess(false)
                .setCompleted(false)
                .setRequestContent(JsonUtils.toJson(message))
                .setCreatedTime(now)
                .setUpdatedTime(now);
        rt.notifyLogStore.save(notifyLog);
        rt.notifier.submit(notifyLog);
    }
}
