package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.api.RefundQueryRequest;
import com.sfi.conflux.pay.api.RefundRequest;
import com.sfi.conflux.pay.api.RefundResult;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
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
                .setConfigId(request.getConfigId())
                .setBizRefundNo(request.getBizRefundNo())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(channel)
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setReason(request.getReason())
                .setNotifyUrl(request.getNotifyUrl())
                .setState(RefundState.CREATED)
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()));
        Payment payment = new Payment()
                .setConfigId(request.getConfigId())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(channel)
                .setAmount(request.getTotalAmount())
                .setChannelOrderNo(request.getChannelOrderNo());
        try {
            RefundHandler handler = rt.registry.refundHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), channel);
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
                .setConfigId(request.getConfigId())
                .setBizRefundNo(request.getBizRefundNo())
                .setChannelCode(request.getChannelCode())
                .setChannelRefundNo(request.getChannelRefundNo())
                .setBizOrderNo(request.getBizOrderNo());
        try {
            RefundHandler handler = rt.registry.refundHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), request.getChannelCode());
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
