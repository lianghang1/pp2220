package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.api.TransferQueryRequest;
import com.sfi.conflux.pay.api.TransferRequest;
import com.sfi.conflux.pay.api.TransferResult;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.TransferHandler;
import com.sfi.conflux.pay.channel.TransferInvocation;
import com.sfi.conflux.pay.channel.TransferSnapshot;
import com.sfi.conflux.pay.model.Transfer;
import com.sfi.conflux.pay.model.TransferState;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.MaskUtils;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 转账编排器：发起转账、转账查单的渠道编排。
 *
 * <p>SDK 无状态：不落库、不推进状态；渠道以业务转账单号为幂等键，
 * 重复提交由渠道侧保证一致。</p>
 *
 * @author lianghang
 */
@Slf4j
class TransferOrchestrator {

    private final GatewayRuntime rt;

    TransferOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起转账：调用渠道并归一返回受理结果。 */
    TransferResult create(TransferRequest request) {
        request.check();
        PayMethod method = PayMethod.of(request.getMethod());
        String channel = method.getDefaultChannel();
        if (!rt.registry.provider(channel).supportedMethods().contains(method.getCode())) {
            throw GatewayException.of(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "渠道[{}]不支持转账方式[{}]", channel, method.getCode());
        }

        Transfer transfer = new Transfer()
                .setConfigId(request.getConfigId())
                .setBizTransferNo(request.getBizTransferNo())
                .setMethod(method.getCode())
                .setChannelCode(channel)
                .setPayeeAccount(request.getPayeeAccount())
                .setPayeeName(request.getPayeeName())
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setRemark(request.getRemark())
                .setNotifyUrl(request.getNotifyUrl())
                .setState(TransferState.CREATED)
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()));
        try {
            TransferHandler handler = rt.registry.transferHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), channel);
            TransferInvocation invocation = handler.createTransfer(transfer, config);
            if (StringUtils.isNotBlank(invocation.getChannelTransferNo())) {
                transfer.setChannelTransferNo(invocation.getChannelTransferNo());
            }
            transfer.setErrorCode(null);
            transfer.setErrorMsg(null);
            TransferState target = invocation.getState() == null ? TransferState.PROCESSING : invocation.getState();
            transfer.setState(target);
            if (target == TransferState.SUCCEEDED) {
                transfer.setSucceedTime(rt.now());
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道转账调用失败: " + e.getMessage(), e);
        }
        return TransferResult.of(transfer);
    }

    /** 转账查单：向渠道查询并归一返回快照。 */
    TransferResult query(TransferQueryRequest request) {
        request.check();
        Transfer transfer = new Transfer()
                .setConfigId(request.getConfigId())
                .setBizTransferNo(request.getBizTransferNo())
                .setChannelCode(request.getChannelCode())
                .setChannelTransferNo(request.getChannelTransferNo());
        try {
            TransferHandler handler = rt.registry.transferHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), request.getChannelCode());
            TransferSnapshot snapshot = handler.queryTransfer(transfer, config);
            if (StringUtils.isNotBlank(snapshot.getChannelTransferNo())
                    && StringUtils.isBlank(transfer.getChannelTransferNo())) {
                transfer.setChannelTransferNo(snapshot.getChannelTransferNo());
            }
            if (snapshot.getState() != null) {
                transfer.setState(snapshot.getState());
            }
            if (snapshot.getSucceedTime() != null) {
                transfer.setSucceedTime(snapshot.getSucceedTime());
            }
        } catch (GatewayException e) {
            log.warn("渠道转账查单失败: bizTransferNo={}, payee={}, error={}",
                    request.getBizTransferNo(), MaskUtils.custom(request.getBizTransferNo(), 3, 2), e.getMessage());
            throw e;
        }
        return TransferResult.of(transfer);
    }
}
