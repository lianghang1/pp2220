package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackReply;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.channel.CallbackConfigLookup;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.ProfitShare;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 渠道回调分发器：定位渠道回调处理器 → 验签解析 → 按单据类型应用状态迁移 →
 * 组装渠道应答。
 *
 * <p>验签失败、报文不合法、订单不存在、金额不一致等异常直接上抛，
 * 使用方捕获后可用渠道处理器的 {@code failureReply()} 应答渠道触发重推；
 * 正常处理返回 {@code successReply()}。回调可能重复到达（渠道重推），
 * 状态迁移按幂等语义处理。</p>
 *
 * @author lianghang
 */
@Slf4j
class CallbackDispatcher {

    private final GatewayRuntime rt;

    CallbackDispatcher(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 处理渠道回调。 */
    ChannelCallbackReply handle(ChannelCallbackRequest raw) {
        if (raw == null || StringUtils.isBlank(raw.getChannelCode())) {
            throw GatewayException.of(CommonErrorCode.BAD_REQUEST, "回调请求缺少渠道编码");
        }
        normalizeHeaders(raw);
        ChannelCallbackHandler handler = rt.registry.callbackHandler(raw.getChannelCode());
        CallbackEvent event = handler.parseCallback(raw, new CallbackConfigLookup() {
            @Override
            public ChannelConfig byAccountKey(String accountKey) {
                return rt.configProvider.getByAccountKey(raw.getChannelCode(), accountKey);
            }
        });
        if (event == null || event.getType() == null) {
            throw GatewayException.of(GatewayErrorCode.CALLBACK_PARSE_FAILED, "回调解析结果为空");
        }
        switch (event.getType()) {
            case PAYMENT:
                applyPaymentEvent(event);
                break;
            case REFUND:
                applyRefundEvent(event);
                break;
            case TRANSFER:
                applyTransferEvent(event);
                break;
            case PROFIT_SHARE:
                applyProfitShareEvent(event);
                break;
            default:
                throw GatewayException.of(GatewayErrorCode.CALLBACK_PARSE_FAILED,
                        "未知回调单据类型: {}", event.getType());
        }
        return ChannelCallbackReply.of(200, handler.successReply());
    }

    /**
     * 请求头归一化为大小写不敏感视图：HTTP 头名本就不区分大小写，
     * 不同 Web 容器传给使用方的头名大小写各异，渠道验签按规范名查找时必须免疫差异。
     */
    private void normalizeHeaders(ChannelCallbackRequest raw) {
        Map<String, String> headers = raw.getHeaders();
        if (headers == null) {
            raw.setHeaders(new HashMap<String, String>());
            return;
        }
        if (!(headers instanceof TreeMap)) {
            Map<String, String> caseInsensitive = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(headers);
            raw.setHeaders(caseInsensitive);
        }
    }

    private void applyPaymentEvent(CallbackEvent event) {
        Payment payment = rt.paymentStore.findByBizOrderNo(event.getAppId(), event.getBizOrderNo());
        if (payment == null && StringUtils.isNotBlank(event.getChannelOrderNo())) {
            payment = rt.paymentStore.findByChannelOrderNo(event.getChannelOrderNo());
        }
        if (payment == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "回调支付单不存在: appId={}, bizOrderNo={}", event.getAppId(), event.getBizOrderNo());
        }
        if (event.getAmount() != null && !event.getAmount().equals(payment.getAmount())) {
            throw GatewayException.of(GatewayErrorCode.AMOUNT_MISMATCH,
                    "回调金额与订单不一致: paymentId={}, order={}, notify={}",
                    payment.getPaymentId(), payment.getAmount(), event.getAmount());
        }
        if (event.isSuccess()) {
            if (payment.getState().isTerminal()) {
                log.info("忽略已终态支付单的成功回调: paymentId={}, state={}", payment.getPaymentId(), payment.getState());
                return;
            }
            if (StringUtils.isNotBlank(event.getChannelOrderNo())
                    && StringUtils.isBlank(payment.getChannelOrderNo())) {
                payment.setChannelOrderNo(event.getChannelOrderNo());
            }
            rt.transitions.markPaymentSucceeded(payment,
                    event.getOccurTime() != null ? event.getOccurTime() : rt.now());
        } else {
            if (payment.getState().isTerminal()) {
                return;
            }
            rt.transitions.markPaymentFailed(payment, "CHANNEL_CALLBACK", "渠道通知支付失败");
        }
    }

    private void applyRefundEvent(CallbackEvent event) {
        Refund refund = rt.refundStore == null ? null
                : rt.refundStore.findByBizRefundNo(event.getAppId(), event.getBizOrderNo());
        if (refund == null && rt.refundStore != null && StringUtils.isNotBlank(event.getChannelOrderNo())) {
            refund = rt.refundStore.findByChannelRefundNo(event.getChannelOrderNo());
        }
        if (refund == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "回调退款单不存在: appId={}, bizOrderNo={}", event.getAppId(), event.getBizOrderNo());
        }
        if (event.isSuccess()) {
            if (refund.getState().isTerminal()) {
                return;
            }
            if (StringUtils.isNotBlank(event.getChannelOrderNo())
                    && StringUtils.isBlank(refund.getChannelRefundNo())) {
                refund.setChannelRefundNo(event.getChannelOrderNo());
            }
            rt.transitions.onRefundSucceeded(refund, rt.paymentStore.findById(refund.getPaymentId()));
        } else {
            if (refund.getState().isTerminal()) {
                return;
            }
            rt.transitions.onRefundFailed(refund);
        }
    }

    private void applyTransferEvent(CallbackEvent event) {
        Transfer transfer = rt.transferStore == null ? null
                : rt.transferStore.findByBizTransferNo(event.getAppId(), event.getBizOrderNo());
        if (transfer == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "回调转账单不存在: appId={}, bizOrderNo={}", event.getAppId(), event.getBizOrderNo());
        }
        if (transfer.getState().isTerminal()) {
            return;
        }
        if (StringUtils.isNotBlank(event.getChannelOrderNo())
                && StringUtils.isBlank(transfer.getChannelTransferNo())) {
            transfer.setChannelTransferNo(event.getChannelOrderNo());
        }
        rt.transitions.onTransferFinished(transfer,
                event.isSuccess() ? io.github.lianghang1.conflux.pay.model.TransferState.SUCCEEDED
                        : io.github.lianghang1.conflux.pay.model.TransferState.FAILED,
                event.getOccurTime());
    }

    private void applyProfitShareEvent(CallbackEvent event) {
        ProfitShare share = rt.profitShareStore == null ? null
                : rt.profitShareStore.findByBizShareNo(event.getAppId(), event.getBizOrderNo());
        if (share == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "回调分账单不存在: appId={}, bizOrderNo={}", event.getAppId(), event.getBizOrderNo());
        }
        if (share.getState().isTerminal()) {
            return;
        }
        if (StringUtils.isNotBlank(event.getChannelOrderNo())
                && StringUtils.isBlank(share.getChannelShareNo())) {
            share.setChannelShareNo(event.getChannelOrderNo());
        }
        rt.transitions.onProfitShareFinished(share,
                event.isSuccess() ? io.github.lianghang1.conflux.pay.model.ProfitShareState.FINISHED
                        : io.github.lianghang1.conflux.pay.model.ProfitShareState.FAILED,
                event.getOccurTime());
    }
}
