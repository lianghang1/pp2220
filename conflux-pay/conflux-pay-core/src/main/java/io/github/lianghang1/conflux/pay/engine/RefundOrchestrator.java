package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.RefundHandler;
import io.github.lianghang1.conflux.pay.channel.RefundInvocation;
import io.github.lianghang1.conflux.pay.channel.RefundSnapshot;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 退款编排器：发起退款、退款查单的渠道编排。
 *
 * <p>SDK 无状态：不落库、不校验可退余额（业务规则由使用方负责）、不推进状态。
 * 原单金额（{@code totalAmount}）与原单渠道单号由使用方随请求提供；
 * 渠道以业务退款单号为幂等键，重复提交由渠道侧保证一致。</p>
 *
 * @author lianghang
 */
@Slf4j
class RefundOrchestrator {

    private final GatewayRuntime rt;

    RefundOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起退款：调用渠道并归一返回受理结果。 */
    RefundResult create(RefundRequest request) {
        request.check();
        String channel = request.getChannelCode();
        Refund refund = new Refund()
                .setAppId(request.getAppId())
                .setBizRefundNo(request.getBizRefundNo())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(channel)
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setReason(request.getReason())
                .setState(RefundState.CREATED)
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()));
        Payment payment = new Payment()
                .setAppId(request.getAppId())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(channel)
                .setAmount(request.getTotalAmount())
                .setChannelOrderNo(request.getChannelOrderNo());
        try {
            RefundHandler handler = rt.registry.refundHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), channel);
            RefundInvocation invocation = handler.createRefund(refund, payment, config);
            if (StringUtils.isNotBlank(invocation.getChannelRefundNo())) {
                refund.setChannelRefundNo(invocation.getChannelRefundNo());
            }
            refund.setErrorCode(null);
            refund.setErrorMsg(null);
            RefundState target = invocation.getState() == null ? RefundState.PROCESSING : invocation.getState();
            refund.setState(target);
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道退款调用失败: " + e.getMessage(), e);
        }
        return RefundResult.of(refund);
    }

    /** 退款查单：向渠道查询并归一返回快照。 */
    RefundResult query(RefundQueryRequest request) {
        request.check();
        Refund refund = new Refund()
                .setAppId(request.getAppId())
                .setBizRefundNo(request.getBizRefundNo())
                .setChannelCode(request.getChannelCode())
                .setChannelRefundNo(request.getChannelRefundNo())
                .setBizOrderNo(request.getBizOrderNo());
        try {
            RefundHandler handler = rt.registry.refundHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), request.getChannelCode());
            RefundSnapshot snapshot = handler.queryRefund(refund, config);
            if (StringUtils.isNotBlank(snapshot.getChannelRefundNo())
                    && StringUtils.isBlank(refund.getChannelRefundNo())) {
                refund.setChannelRefundNo(snapshot.getChannelRefundNo());
            }
            if (snapshot.getState() != null) {
                refund.setState(snapshot.getState());
            }
            if (snapshot.getSucceedTime() != null) {
                refund.setSucceedTime(snapshot.getSucceedTime());
            }
        } catch (GatewayException e) {
            throw e;
        }
        return RefundResult.of(refund);
    }
}
