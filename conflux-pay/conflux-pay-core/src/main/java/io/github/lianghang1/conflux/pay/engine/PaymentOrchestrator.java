package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentHandler;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.model.PayPayloadType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付编排器：下单、查单、关单的渠道编排。
 *
 * <p>SDK 无状态：不落库、不推进状态、不生成内部单号——渠道商户单号即使用方
 * 业务单号（{@code bizOrderNo}），渠道回传的业务单号可直接定位使用方订单。
 * 结果为渠道归一快照，状态推进、幂等判重、业务联动由使用方完成。</p>
 *
 * @author lianghang
 */
@Slf4j
class PaymentOrchestrator {

    private final GatewayRuntime rt;

    PaymentOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 统一下单：调用渠道并归一返回受理结果。 */
    PaymentResult create(PaymentRequest request) {
        request.check();
        PayMethod method = PayMethod.of(request.getMethod());
        String channel = StringUtils.isNotBlank(request.getChannelCode())
                ? request.getChannelCode() : method.getDefaultChannel();
        assertMethodSupported(channel, method);

        Payment payment = buildPayment(request, method, channel);
        try {
            PaymentHandler handler = rt.registry.paymentHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), channel);
            PaymentInvocation invocation = handler.createPayment(payment, config);
            if (StringUtils.isNotBlank(invocation.getChannelOrderNo())) {
                payment.setChannelOrderNo(invocation.getChannelOrderNo());
            }
            if (invocation.getPayload() != null && invocation.getPayload().getType() != PayPayloadType.NONE) {
                payment.setPayPayload(JsonUtils.toJson(invocation.getPayload()));
            }
            payment.setErrorCode(null);
            payment.setErrorMsg(null);
            if (invocation.getState() == PaymentState.SUCCEEDED) {
                payment.setState(PaymentState.SUCCEEDED);
                payment.setSucceedTime(rt.now());
            } else {
                payment.setState(PaymentState.PAYING);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道调用失败: " + e.getMessage(), e);
        }
        return PaymentResult.of(payment);
    }

    /** 查单：向渠道查询并归一返回快照。 */
    PaymentResult query(PaymentQueryRequest request) {
        request.check();
        Payment payment = locatePayment(request);
        try {
            PaymentHandler handler = rt.registry.paymentHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), request.getChannelCode());
            PaymentSnapshot snapshot = handler.queryPayment(payment, config);
            applySnapshot(payment, snapshot);
        } catch (GatewayException e) {
            throw e;
        }
        return PaymentResult.of(payment);
    }

    /** 关单：调用渠道关单；渠道报已支付时复查一次并按渠道结果返回。 */
    PaymentResult close(PaymentQueryRequest request) {
        request.check();
        Payment payment = locatePayment(request);
        try {
            PaymentHandler handler = rt.registry.paymentHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), request.getChannelCode());
            handler.closePayment(payment, config);
            payment.setState(PaymentState.CLOSED);
        } catch (GatewayException e) {
            // 渠道拒绝关单（如已支付）时复查一次，按渠道真实状态返回
            try {
                PaymentHandler handler = rt.registry.paymentHandler(request.getChannelCode());
                ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), request.getChannelCode());
                PaymentSnapshot snapshot = handler.queryPayment(payment, config);
                if (snapshot.getState() == PaymentState.SUCCEEDED) {
                    applySnapshot(payment, snapshot);
                    return PaymentResult.of(payment);
                }
            } catch (GatewayException ignore) {
                log.warn("关单失败后渠道复查未成功: bizOrderNo={}", request.getBizOrderNo());
            }
            throw e;
        }
        return PaymentResult.of(payment);
    }

    /** 按请求参数构建渠道交互载体（不落库）。 */
    private Payment locatePayment(PaymentQueryRequest request) {
        return new Payment()
                .setAppId(request.getAppId())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(request.getChannelCode())
                .setChannelOrderNo(request.getChannelOrderNo());
    }

    private Payment buildPayment(PaymentRequest request, PayMethod method, String channel) {
        return new Payment()
                .setAppId(request.getAppId())
                .setBizOrderNo(request.getBizOrderNo())
                .setMethod(method.getCode())
                .setChannelCode(channel)
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setSubject(request.getSubject())
                .setBody(request.getBody())
                .setClientIp(request.getClientIp())
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()));
    }

    private void applySnapshot(Payment payment, PaymentSnapshot snapshot) {
        if (StringUtils.isNotBlank(snapshot.getChannelOrderNo())
                && StringUtils.isBlank(payment.getChannelOrderNo())) {
            payment.setChannelOrderNo(snapshot.getChannelOrderNo());
        }
        if (snapshot.getState() != null) {
            payment.setState(snapshot.getState());
        }
        if (snapshot.getSucceedTime() != null) {
            payment.setSucceedTime(snapshot.getSucceedTime());
        }
    }

    private void assertMethodSupported(String channel, PayMethod method) {
        if (!rt.registry.provider(channel).supportedMethods().contains(method.getCode())) {
            throw GatewayException.of(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "渠道[{}]不支持支付方式[{}]", channel, method.getCode());
        }
    }
}
