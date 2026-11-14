package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.RefundHandler;
import io.github.lianghang1.paygateway.channel.RefundInvocation;
import io.github.lianghang1.paygateway.channel.RefundSnapshot;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.Refund;
import io.github.lianghang1.paygateway.model.RefundState;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信退款处理器：按商户退款单号（outRefundNo = 网关退款单号）发起与查询，
 * 支持服务商模式（subMchNo 存在时携带特约商户号）。
 *
 * <p>退款回调地址复用渠道参数 notifyUrl；微信退款支持异步回调与查单两种终态获取方式。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatRefundHandler implements RefundHandler {

    private final WechatClientHolder holder;

    public WechatRefundHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        CreateRequest request = new CreateRequest();
        if (config.getSubMchNo() != null) {
            request.setSubMchid(config.getSubMchNo());
        }
        request.setOutTradeNo(payment.getPaymentId());
        request.setOutRefundNo(refund.getRefundId());
        request.setReason(refund.getReason());
        request.setNotifyUrl(params.getNotifyUrl());
        com.wechat.pay.java.service.refund.model.AmountReq amount =
                new com.wechat.pay.java.service.refund.model.AmountReq();
        amount.setRefund(refund.getAmount());
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        com.wechat.pay.java.service.refund.model.Refund response =
                wrap("退款", () -> holder.clients(config).refundService().create(request));
        return new RefundInvocation()
                .setChannelRefundNo(response.getRefundId())
                .setState(WechatStateTranslator.toRefundState(
                        response.getStatus() == null ? null : response.getStatus().name()));
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(refund.getRefundId());
        if (config.getSubMchNo() != null) {
            request.setSubMchid(config.getSubMchNo());
        }
        com.wechat.pay.java.service.refund.model.Refund response =
                wrap("退款查单", () -> holder.clients(config).refundService().queryByOutRefundNo(request));
        return new RefundSnapshot()
                .setChannelRefundNo(response.getRefundId())
                .setState(WechatStateTranslator.toRefundState(
                        response.getStatus() == null ? null : response.getStatus().name()))
                .setSucceedTime(WechatStateTranslator.toLocalDateTime(response.getSuccessTime()))
                .setRawMsg(response.getStatus() == null ? null : response.getStatus().name());
    }

    private <T> T wrap(String action, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (Exception e) {
            throw new io.github.lianghang1.paygateway.exception.GatewayException(
                    io.github.lianghang1.paygateway.exception.GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
