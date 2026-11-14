package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentHandler;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.event.PaymentEvent;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.PayPayloadType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.IdGenerator;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付编排器：下单（含幂等与渠道重试语义）、查单（含补单）、关单。
 *
 * <p>下单幂等约定：同一 {@code appId + bizOrderNo} 的订单——
 * ① 已取得唤起参数或已离开创建态：参数一致则幂等返回既有订单；
 * ② 仍处创建态且无唤起参数（此前渠道调用失败）：视为重试，重新发起渠道调用；
 * ③ 参数不一致：抛重复订单异常。渠道调用失败时订单保持在创建态并记录错误信息，
 * 使用方可携带相同参数重试。</p>
 *
 * @author lianghang
 */
@Slf4j
class PaymentOrchestrator {

    private final GatewayRuntime rt;

    PaymentOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 统一下单。 */
    PaymentResult create(PaymentRequest request) {
        request.check();
        PayMethod method = PayMethod.of(request.getMethod());
        String channel = StringUtils.isNotBlank(request.getChannelCode())
                ? request.getChannelCode() : method.getDefaultChannel();

        Payment payment = rt.paymentStore.findByBizOrderNo(request.getAppId(), request.getBizOrderNo());
        if (payment == null) {
            payment = buildPayment(request, method, channel);
            rt.paymentStore.save(payment);
            rt.publisher.publishPayment(PaymentEvent.EventType.PAYMENT_CREATED, payment);
        } else {
            assertSameIntent(payment, request, method, channel);
            boolean retryable = payment.getState() == PaymentState.CREATED
                    && StringUtils.isBlank(payment.getPayPayload());
            if (!retryable) {
                return PaymentResult.of(payment);
            }
            log.info("订单处于创建态且无唤起参数，按重试语义重新发起渠道调用: paymentId={}", payment.getPaymentId());
        }

        assertMethodSupported(channel, method);
        invokeChannel(payment);
        return PaymentResult.of(payment);
    }

    /** 查单：非终态订单先向渠道复核（兼做补单），再返回本地最新状态。 */
    PaymentResult query(PaymentQueryRequest request) {
        Payment payment = locate(request);
        refreshFromChannel(payment);
        return PaymentResult.of(payment);
    }

    /** 关单：渠道侧已支付时自动复查并按结果修正本地状态。 */
    PaymentResult close(String paymentId) {
        GatewayAssert.notBlank(paymentId, CommonErrorCode.BAD_REQUEST, "paymentId 不能为空");
        Payment payment = rt.paymentStore.findById(paymentId);
        if (payment == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND, "支付单不存在: {}", paymentId);
        }
        if (payment.getState() == PaymentState.CLOSED) {
            return PaymentResult.of(payment);
        }
        if (payment.getState().isPaid()) {
            throw GatewayException.of(GatewayErrorCode.STATE_ILLEGAL,
                    "订单已支付，不允许关闭: paymentId={}", paymentId);
        }
        PaymentHandler handler = rt.registry.paymentHandler(payment.getChannelCode());
        ChannelConfig config = rt.configProvider.getConfig(payment.getAppId(), payment.getChannelCode());
        try {
            handler.closePayment(payment, config);
        } catch (GatewayException e) {
            PaymentSnapshot snapshot = handler.queryPayment(payment, config);
            if (snapshot.getState() == PaymentState.SUCCEEDED) {
                rt.transitions.markPaymentSucceeded(payment, snapshot.getSucceedTime());
                return PaymentResult.of(payment);
            }
            throw e;
        }
        rt.transitions.markPaymentClosed(payment);
        return PaymentResult.of(payment);
    }

    private Payment buildPayment(PaymentRequest request, PayMethod method, String channel) {
        return new Payment()
                .setPaymentId(IdGenerator.nextPaymentId())
                .setAppId(request.getAppId())
                .setBizOrderNo(request.getBizOrderNo())
                .setMethod(method.getCode())
                .setChannelCode(channel)
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setSubject(request.getSubject())
                .setBody(request.getBody())
                .setClientIp(request.getClientIp())
                .setNotifyUrl(request.getNotifyUrl())
                .setState(PaymentState.CREATED)
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()))
                .setExpiredTime(request.getExpireSeconds() == null ? null : rt.now().plusSeconds(request.getExpireSeconds()))
                .setCreatedTime(rt.now())
                .setUpdatedTime(rt.now());
    }

    private void assertSameIntent(Payment existing, PaymentRequest request, PayMethod method, String channel) {
        boolean same = existing.getAmount() != null && existing.getAmount().equals(request.getAmount())
                && method.getCode().equals(existing.getMethod())
                && channel.equals(existing.getChannelCode());
        if (!same) {
            throw GatewayException.of(GatewayErrorCode.DUPLICATE_ORDER,
                    "相同业务单号的订单已存在且参数不一致: bizOrderNo={}", request.getBizOrderNo());
        }
    }

    private void assertMethodSupported(String channel, PayMethod method) {
        if (!rt.registry.provider(channel).supportedMethods().contains(method.getCode())) {
            throw GatewayException.of(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "渠道[{}]不支持支付方式[{}]", channel, method.getCode());
        }
    }

    private void invokeChannel(Payment payment) {
        try {
            PaymentHandler handler = rt.registry.paymentHandler(payment.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(payment.getAppId(), payment.getChannelCode());
            PaymentInvocation invocation = handler.createPayment(payment, config);
            if (StringUtils.isNotBlank(invocation.getChannelOrderNo())) {
                payment.setChannelOrderNo(invocation.getChannelOrderNo());
            }
            if (invocation.getPayload() != null && invocation.getPayload().getType() != PayPayloadType.NONE) {
                payment.setPayPayload(JsonUtils.toJson(invocation.getPayload()));
            }
            payment.setErrorCode(null);
            payment.setErrorMsg(null);
            payment.setUpdatedTime(rt.now());
            if (invocation.getState() == PaymentState.SUCCEEDED) {
                rt.transitions.markPaymentSucceeded(payment, rt.now());
            } else {
                payment.setState(PaymentState.PAYING);
                rt.paymentStore.update(payment);
                rt.publisher.publishPayment(PaymentEvent.EventType.PAYMENT_PAYING, payment);
            }
        } catch (GatewayException e) {
            recordError(payment, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            GatewayException wrapped = new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道调用失败: " + e.getMessage(), e);
            recordError(payment, wrapped.getErrorCode().getCode(), wrapped.getMessage());
            throw wrapped;
        }
    }

    private void recordError(Payment payment, int errorCode, String errorMsg) {
        payment.setErrorCode(String.valueOf(errorCode));
        payment.setErrorMsg(StringUtils.abbreviate(errorMsg, 512));
        payment.setUpdatedTime(rt.now());
        rt.paymentStore.update(payment);
    }

    private Payment locate(PaymentQueryRequest request) {
        if (StringUtils.isNotBlank(request.getPaymentId())) {
            Payment payment = rt.paymentStore.findById(request.getPaymentId());
            if (payment != null) {
                return payment;
            }
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "支付单不存在: {}", request.getPaymentId());
        }
        GatewayAssert.notBlank(request.getAppId(), CommonErrorCode.BAD_REQUEST,
                "paymentId 与 appId 至少提供一个");
        GatewayAssert.notBlank(request.getBizOrderNo(), CommonErrorCode.BAD_REQUEST,
                "paymentId 与 bizOrderNo 至少提供一个");
        Payment payment = rt.paymentStore.findByBizOrderNo(request.getAppId(), request.getBizOrderNo());
        if (payment == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "支付单不存在: appId={}, bizOrderNo={}", request.getAppId(), request.getBizOrderNo());
        }
        return payment;
    }

    private void refreshFromChannel(Payment payment) {
        if (payment.getState().isTerminal()) {
            return;
        }
        try {
            PaymentHandler handler = rt.registry.paymentHandler(payment.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(payment.getAppId(), payment.getChannelCode());
            PaymentSnapshot snapshot = handler.queryPayment(payment, config);
            boolean dirty = false;
            if (StringUtils.isNotBlank(snapshot.getChannelOrderNo())
                    && StringUtils.isBlank(payment.getChannelOrderNo())) {
                payment.setChannelOrderNo(snapshot.getChannelOrderNo());
                dirty = true;
            }
            if (snapshot.getState() == PaymentState.SUCCEEDED) {
                rt.transitions.markPaymentSucceeded(payment, snapshot.getSucceedTime());
            } else if (snapshot.getState() == PaymentState.FAILED) {
                rt.transitions.markPaymentFailed(payment, "CHANNEL_STATE",
                        StringUtils.abbreviate(snapshot.getRawMsg(), 512));
            } else if (snapshot.getState() == PaymentState.CLOSED) {
                rt.transitions.markPaymentClosed(payment);
            } else if (dirty) {
                payment.setUpdatedTime(rt.now());
                rt.paymentStore.update(payment);
            }
        } catch (GatewayException e) {
            log.warn("渠道查单失败，保留本地状态: paymentId={}, error={}", payment.getPaymentId(), e.getMessage());
        }
    }
}
