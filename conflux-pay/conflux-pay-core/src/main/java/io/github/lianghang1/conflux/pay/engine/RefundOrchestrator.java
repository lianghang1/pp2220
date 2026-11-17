package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.RefundHandler;
import io.github.lianghang1.conflux.pay.channel.RefundInvocation;
import io.github.lianghang1.conflux.pay.channel.RefundSnapshot;
import io.github.lianghang1.conflux.pay.event.PaymentEvent;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.common.util.IdGenerator;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 退款编排器：发起退款（幂等 + 可退余额校验）、退款查单（兼做补单）。
 *
 * <p>退款成功后联动重算支付单的退款聚合状态（PART_REFUNDED / REFUNDED）。
 * 渠道调用失败时退款单保留创建态并记录错误信息，使用方可携带相同参数重试。</p>
 *
 * @author lianghang
 */
@Slf4j
class RefundOrchestrator {

    private final GatewayRuntime rt;

    RefundOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起退款。 */
    RefundResult create(RefundRequest request) {
        if (rt.refundStore == null) {
            throw GatewayException.of(GatewayErrorCode.STORE_MISSING, "未注册退款单存储，退款功能不可用");
        }
        request.check();
        Payment payment = locatePayment(request);
        if (payment.getState() != PaymentState.SUCCEEDED && payment.getState() != PaymentState.PART_REFUNDED) {
            throw GatewayException.of(GatewayErrorCode.STATE_ILLEGAL,
                    "订单当前状态不允许退款: paymentId={}, state={}", payment.getPaymentId(), payment.getState());
        }
        String channel = payment.getChannelCode();

        Refund existing = rt.refundStore.findByBizRefundNo(request.getAppId(), request.getBizRefundNo());
        if (existing != null) {
            boolean same = existing.getPaymentId().equals(payment.getPaymentId())
                    && existing.getAmount().equals(request.getAmount());
            if (!same) {
                throw GatewayException.of(GatewayErrorCode.DUPLICATE_ORDER,
                        "相同业务退款单号的退款已存在且参数不一致: bizRefundNo={}", request.getBizRefundNo());
            }
            return RefundResult.of(existing);
        }

        assertRefundable(payment, request.getAmount());

        Refund refund = new Refund()
                .setRefundId(IdGenerator.nextRefundId())
                .setAppId(request.getAppId())
                .setBizRefundNo(request.getBizRefundNo())
                .setPaymentId(payment.getPaymentId())
                .setBizOrderNo(payment.getBizOrderNo())
                .setChannelCode(channel)
                .setAmount(request.getAmount())
                .setCurrency(StringUtils.isNotBlank(request.getCurrency()) ? request.getCurrency() : payment.getCurrency())
                .setReason(request.getReason())
                .setState(RefundState.CREATED)
                .setNotifyUrl(request.getNotifyUrl())
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()))
                .setCreatedTime(rt.now())
                .setUpdatedTime(rt.now());
        rt.refundStore.save(refund);
        rt.publisher.publishRefund(PaymentEvent.EventType.REFUND_CREATED, refund);

        try {
            RefundHandler handler = rt.registry.refundHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(payment.getAppId(), channel);
            RefundInvocation invocation = handler.createRefund(refund, payment, config);
            if (StringUtils.isNotBlank(invocation.getChannelRefundNo())) {
                refund.setChannelRefundNo(invocation.getChannelRefundNo());
            }
            refund.setErrorCode(null);
            refund.setErrorMsg(null);
            refund.setUpdatedTime(rt.now());
            RefundState target = invocation.getState() == null ? RefundState.PROCESSING : invocation.getState();
            if (target == RefundState.SUCCEEDED) {
                rt.transitions.onRefundSucceeded(refund, payment);
            } else if (target == RefundState.FAILED) {
                rt.transitions.onRefundFailed(refund);
            } else {
                refund.setState(target);
                rt.refundStore.update(refund);
            }
        } catch (GatewayException e) {
            recordError(refund, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            GatewayException wrapped = new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道退款调用失败: " + e.getMessage(), e);
            recordError(refund, wrapped.getErrorCode().getCode(), wrapped.getMessage());
            throw wrapped;
        }
        return RefundResult.of(refund);
    }

    /** 退款查单：处理中退款单先向渠道复核（兼做补单）。 */
    RefundResult query(RefundQueryRequest request) {
        Refund refund = locateRefund(request);
        if (refund.getState() == RefundState.CREATED || refund.getState() == RefundState.PROCESSING) {
            refreshFromChannel(refund);
        }
        return RefundResult.of(refund);
    }

    private void assertRefundable(Payment payment, Long amount) {
        List<Refund> refunds = rt.refundStore.findByPaymentId(payment.getPaymentId());
        long occupied = 0L;
        for (Refund refund : refunds) {
            // 成功、在途（创建/处理中）均占用可退额度，避免并发/在途场景下重复动用同一笔余额；
            // 失败与关闭不占用（可重新发起）。
            if (refund.getState() != RefundState.FAILED
                    && refund.getState() != RefundState.CLOSED
                    && refund.getAmount() != null) {
                occupied += refund.getAmount();
            }
        }
        long refundable = payment.getAmount() - occupied;
        if (amount > refundable) {
            throw GatewayException.of(GatewayErrorCode.REFUND_EXCEED_REFUNDABLE,
                    "退款金额超出可退余额: 可退={}, 本次={}", refundable, amount);
        }
    }

    private Payment locatePayment(RefundRequest request) {
        Payment payment = StringUtils.isNotBlank(request.getPaymentId())
                ? rt.paymentStore.findById(request.getPaymentId())
                : rt.paymentStore.findByBizOrderNo(request.getAppId(), request.getBizOrderNo());
        if (payment == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "支付单不存在: paymentId={}, bizOrderNo={}", request.getPaymentId(), request.getBizOrderNo());
        }
        return payment;
    }

    private Refund locateRefund(RefundQueryRequest request) {
        Refund refund = StringUtils.isNotBlank(request.getRefundId())
                ? rt.refundStore.findById(request.getRefundId())
                : rt.refundStore.findByBizRefundNo(request.getAppId(), request.getBizRefundNo());
        if (refund == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "退款单不存在: refundId={}, bizRefundNo={}", request.getRefundId(), request.getBizRefundNo());
        }
        return refund;
    }

    private void refreshFromChannel(Refund refund) {
        try {
            RefundHandler handler = rt.registry.refundHandler(refund.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(refund.getAppId(), refund.getChannelCode());
            RefundSnapshot snapshot = handler.queryRefund(refund, config);
            if (StringUtils.isNotBlank(snapshot.getChannelRefundNo())
                    && StringUtils.isBlank(refund.getChannelRefundNo())) {
                refund.setChannelRefundNo(snapshot.getChannelRefundNo());
            }
            if (snapshot.getState() == RefundState.SUCCEEDED) {
                Payment payment = rt.paymentStore.findById(refund.getPaymentId());
                rt.transitions.onRefundSucceeded(refund, payment);
            } else if (snapshot.getState() == RefundState.FAILED || snapshot.getState() == RefundState.CLOSED) {
                rt.transitions.onRefundFailed(refund);
            } else {
                refund.setUpdatedTime(rt.now());
                rt.refundStore.update(refund);
            }
        } catch (GatewayException e) {
            log.warn("渠道退款查单失败，保留本地状态: refundId={}, error={}", refund.getRefundId(), e.getMessage());
        }
    }

    private void recordError(Refund refund, int errorCode, String errorMsg) {
        refund.setErrorCode(String.valueOf(errorCode));
        refund.setErrorMsg(StringUtils.abbreviate(errorMsg, 512));
        refund.setUpdatedTime(rt.now());
        rt.refundStore.update(refund);
    }
}
