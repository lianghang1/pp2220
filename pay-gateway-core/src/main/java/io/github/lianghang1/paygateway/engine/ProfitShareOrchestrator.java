package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.api.ProfitShareQueryRequest;
import io.github.lianghang1.paygateway.api.ProfitShareRequest;
import io.github.lianghang1.paygateway.api.ProfitShareResult;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.ProfitShareHandler;
import io.github.lianghang1.paygateway.channel.ProfitShareInvocation;
import io.github.lianghang1.paygateway.channel.ProfitShareSnapshot;
import io.github.lianghang1.paygateway.event.PaymentEvent;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.model.ProfitShare;
import io.github.lianghang1.paygateway.model.ProfitShareReceiver;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import io.github.lianghang1.paygateway.util.IdGenerator;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 分账编排器：发起分账（幂等）、分账查单（兼做补单）。
 *
 * <p>分账对象必须为已成功（或部分退款）的支付单；分账总金额由接收方明细合计得出。
 * 渠道调用失败时分账单保留创建态并记录错误信息，使用方可携带相同参数重试。</p>
 *
 * @author lianghang
 */
@Slf4j
class ProfitShareOrchestrator {

    private final GatewayRuntime rt;

    ProfitShareOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起分账。 */
    ProfitShareResult create(ProfitShareRequest request) {
        if (rt.profitShareStore == null) {
            throw GatewayException.of(GatewayErrorCode.STORE_MISSING, "未注册分账单存储，分账功能不可用");
        }
        request.check();
        Payment payment = StringUtils.isNotBlank(request.getPaymentId())
                ? rt.paymentStore.findById(request.getPaymentId())
                : rt.paymentStore.findByBizOrderNo(request.getAppId(), request.getBizOrderNo());
        if (payment == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "支付单不存在: paymentId={}, bizOrderNo={}", request.getPaymentId(), request.getBizOrderNo());
        }
        if (payment.getState() != PaymentState.SUCCEEDED
                && payment.getState() != PaymentState.PART_REFUNDED) {
            throw GatewayException.of(GatewayErrorCode.STATE_ILLEGAL,
                    "订单当前状态不允许分账: paymentId={}, state={}", payment.getPaymentId(), payment.getState());
        }

        ProfitShare existing = rt.profitShareStore.findByBizShareNo(request.getAppId(), request.getBizShareNo());
        if (existing != null) {
            if (!existing.getPaymentId().equals(payment.getPaymentId())) {
                throw GatewayException.of(GatewayErrorCode.DUPLICATE_ORDER,
                        "相同业务分账单号的分账已存在且关联订单不一致: bizShareNo={}", request.getBizShareNo());
            }
            return ProfitShareResult.of(existing);
        }

        long total = 0L;
        for (ProfitShareReceiver receiver : request.getReceivers()) {
            total += receiver.getAmount();
        }

        ProfitShare share = new ProfitShare()
                .setShareId(IdGenerator.nextProfitShareId())
                .setAppId(request.getAppId())
                .setBizShareNo(request.getBizShareNo())
                .setPaymentId(payment.getPaymentId())
                .setBizOrderNo(payment.getBizOrderNo())
                .setChannelCode(payment.getChannelCode())
                .setAmount(total)
                .setReceivers(request.getReceivers())
                .setState(ProfitShareState.CREATED)
                .setNotifyUrl(request.getNotifyUrl())
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()))
                .setCreatedTime(rt.now())
                .setUpdatedTime(rt.now());
        rt.profitShareStore.save(share);
        rt.publisher.publishProfitShare(PaymentEvent.EventType.PROFIT_SHARE_CREATED, share);

        try {
            ProfitShareHandler handler = rt.registry.profitShareHandler(payment.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(payment.getAppId(), payment.getChannelCode());
            ProfitShareInvocation invocation = handler.createProfitShare(share, config);
            if (StringUtils.isNotBlank(invocation.getChannelShareNo())) {
                share.setChannelShareNo(invocation.getChannelShareNo());
            }
            share.setErrorCode(null);
            share.setErrorMsg(null);
            share.setUpdatedTime(rt.now());
            ProfitShareState target = invocation.getState() == null ? ProfitShareState.PROCESSING : invocation.getState();
            if (target.isTerminal()) {
                rt.transitions.onProfitShareFinished(share, target, rt.now());
            } else {
                share.setState(target);
                rt.profitShareStore.update(share);
            }
        } catch (GatewayException e) {
            recordError(share, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            GatewayException wrapped = new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道分账调用失败: " + e.getMessage(), e);
            recordError(share, wrapped.getErrorCode().getCode(), wrapped.getMessage());
            throw wrapped;
        }
        return ProfitShareResult.of(share);
    }

    /** 分账查单：处理中分账单先向渠道复核（兼做补单）。 */
    ProfitShareResult query(ProfitShareQueryRequest request) {
        ProfitShare share = StringUtils.isNotBlank(request.getShareId())
                ? rt.profitShareStore.findById(request.getShareId())
                : rt.profitShareStore.findByBizShareNo(request.getAppId(), request.getBizShareNo());
        if (share == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "分账单不存在: shareId={}, bizShareNo={}", request.getShareId(), request.getBizShareNo());
        }
        if (share.getState() == ProfitShareState.CREATED || share.getState() == ProfitShareState.PROCESSING) {
            refreshFromChannel(share);
        }
        return ProfitShareResult.of(share);
    }

    private void refreshFromChannel(ProfitShare share) {
        try {
            ProfitShareHandler handler = rt.registry.profitShareHandler(share.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(share.getAppId(), share.getChannelCode());
            ProfitShareSnapshot snapshot = handler.queryProfitShare(share, config);
            if (StringUtils.isNotBlank(snapshot.getChannelShareNo())
                    && StringUtils.isBlank(share.getChannelShareNo())) {
                share.setChannelShareNo(snapshot.getChannelShareNo());
            }
            if (snapshot.getState().isTerminal()) {
                rt.transitions.onProfitShareFinished(share, snapshot.getState(), snapshot.getFinishTime());
            } else {
                share.setUpdatedTime(rt.now());
                rt.profitShareStore.update(share);
            }
        } catch (GatewayException e) {
            log.warn("渠道分账查单失败，保留本地状态: shareId={}, error={}", share.getShareId(), e.getMessage());
        }
    }

    private void recordError(ProfitShare share, int errorCode, String errorMsg) {
        share.setErrorCode(String.valueOf(errorCode));
        share.setErrorMsg(StringUtils.abbreviate(errorMsg, 512));
        share.setUpdatedTime(rt.now());
        rt.profitShareStore.update(share);
    }
}
