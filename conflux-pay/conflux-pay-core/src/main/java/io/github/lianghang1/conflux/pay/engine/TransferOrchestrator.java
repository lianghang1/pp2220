package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.TransferQueryRequest;
import io.github.lianghang1.conflux.pay.api.TransferRequest;
import io.github.lianghang1.conflux.pay.api.TransferResult;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.TransferHandler;
import io.github.lianghang1.conflux.pay.channel.TransferInvocation;
import io.github.lianghang1.conflux.pay.channel.TransferSnapshot;
import io.github.lianghang1.conflux.pay.event.PaymentEvent;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.pay.model.TransferState;
import io.github.lianghang1.conflux.common.util.IdGenerator;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.MaskUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 转账编排器：发起转账（幂等）、转账查单（兼做补单）。
 *
 * <p>渠道由转账方式的默认渠道决定；渠道调用失败时转账单保留创建态并记录
 * 错误信息，使用方可携带相同参数重试（渠道幂等以业务转账单号为键，由渠道包保证）。</p>
 *
 * @author lianghang
 */
@Slf4j
class TransferOrchestrator {

    private final GatewayRuntime rt;

    TransferOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起转账。 */
    TransferResult create(TransferRequest request) {
        if (rt.transferStore == null) {
            throw GatewayException.of(GatewayErrorCode.STORE_MISSING, "未注册转账单存储，转账功能不可用");
        }
        request.check();
        PayMethod method = PayMethod.of(request.getMethod());
        String channel = method.getDefaultChannel();

        Transfer existing = rt.transferStore.findByBizTransferNo(request.getAppId(), request.getBizTransferNo());
        if (existing != null) {
            boolean same = existing.getAmount().equals(request.getAmount())
                    && method.getCode().equals(existing.getMethod())
                    && request.getPayeeAccount().equals(existing.getPayeeAccount());
            if (!same) {
                throw GatewayException.of(GatewayErrorCode.DUPLICATE_ORDER,
                        "相同业务转账单号的转账已存在且参数不一致: bizTransferNo={}", request.getBizTransferNo());
            }
            return TransferResult.of(existing);
        }

        Transfer transfer = new Transfer()
                .setTransferId(IdGenerator.nextTransferId())
                .setAppId(request.getAppId())
                .setBizTransferNo(request.getBizTransferNo())
                .setMethod(method.getCode())
                .setChannelCode(channel)
                .setPayeeAccount(request.getPayeeAccount())
                .setPayeeName(request.getPayeeName())
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setRemark(request.getRemark())
                .setState(TransferState.CREATED)
                .setNotifyUrl(request.getNotifyUrl())
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()))
                .setCreatedTime(rt.now())
                .setUpdatedTime(rt.now());
        rt.transferStore.save(transfer);
        rt.publisher.publishTransfer(PaymentEvent.EventType.TRANSFER_CREATED, transfer);

        if (!rt.registry.provider(channel).supportedMethods().contains(method.getCode())) {
            throw GatewayException.of(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "渠道[{}]不支持转账方式[{}]", channel, method.getCode());
        }

        try {
            TransferHandler handler = rt.registry.transferHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getAppId(), channel);
            TransferInvocation invocation = handler.createTransfer(transfer, config);
            if (StringUtils.isNotBlank(invocation.getChannelTransferNo())) {
                transfer.setChannelTransferNo(invocation.getChannelTransferNo());
            }
            transfer.setErrorCode(null);
            transfer.setErrorMsg(null);
            transfer.setUpdatedTime(rt.now());
            TransferState target = invocation.getState() == null ? TransferState.PROCESSING : invocation.getState();
            if (target.isTerminal()) {
                rt.transitions.onTransferFinished(transfer, target, rt.now());
            } else {
                transfer.setState(target);
                rt.transferStore.update(transfer);
            }
        } catch (GatewayException e) {
            recordError(transfer, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            GatewayException wrapped = new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道转账调用失败: " + e.getMessage(), e);
            recordError(transfer, wrapped.getErrorCode().getCode(), wrapped.getMessage());
            throw wrapped;
        }
        return TransferResult.of(transfer);
    }

    /** 转账查单：处理中转账单先向渠道复核（兼做补单）。 */
    TransferResult query(TransferQueryRequest request) {
        Transfer transfer = locate(request);
        if (transfer.getState() == TransferState.CREATED || transfer.getState() == TransferState.PROCESSING) {
            refreshFromChannel(transfer);
        }
        return TransferResult.of(transfer);
    }

    private Transfer locate(TransferQueryRequest request) {
        Transfer transfer = StringUtils.isNotBlank(request.getTransferId())
                ? rt.transferStore.findById(request.getTransferId())
                : rt.transferStore.findByBizTransferNo(request.getAppId(), request.getBizTransferNo());
        if (transfer == null) {
            throw GatewayException.of(GatewayErrorCode.ORDER_NOT_FOUND,
                    "转账单不存在: transferId={}, bizTransferNo={}", request.getTransferId(), request.getBizTransferNo());
        }
        return transfer;
    }

    private void refreshFromChannel(Transfer transfer) {
        try {
            TransferHandler handler = rt.registry.transferHandler(transfer.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(transfer.getAppId(), transfer.getChannelCode());
            TransferSnapshot snapshot = handler.queryTransfer(transfer, config);
            if (StringUtils.isNotBlank(snapshot.getChannelTransferNo())
                    && StringUtils.isBlank(transfer.getChannelTransferNo())) {
                transfer.setChannelTransferNo(snapshot.getChannelTransferNo());
            }
            if (snapshot.getState().isTerminal()) {
                rt.transitions.onTransferFinished(transfer, snapshot.getState(), snapshot.getSucceedTime());
            } else {
                transfer.setUpdatedTime(rt.now());
                rt.transferStore.update(transfer);
            }
        } catch (GatewayException e) {
            log.warn("渠道转账查单失败，保留本地状态: transferId={}, payee={}, error={}",
                    transfer.getTransferId(), MaskUtils.custom(transfer.getPayeeAccount(), 3, 2), e.getMessage());
        }
    }

    private void recordError(Transfer transfer, int errorCode, String errorMsg) {
        transfer.setErrorCode(String.valueOf(errorCode));
        transfer.setErrorMsg(StringUtils.abbreviate(errorMsg, 512));
        transfer.setUpdatedTime(rt.now());
        rt.transferStore.update(transfer);
    }
}
