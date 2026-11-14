package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.service.transferbatch.model.GetTransferBatchByNoRequest;
import com.wechat.pay.java.service.transferbatch.model.InitiateBatchTransferRequest;
import com.wechat.pay.java.service.transferbatch.model.InitiateBatchTransferResponse;
import com.wechat.pay.java.service.transferbatch.model.TransferBatchEntity;
import com.wechat.pay.java.service.transferbatch.model.TransferDetailInput;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.TransferHandler;
import io.github.lianghang1.paygateway.channel.TransferInvocation;
import io.github.lianghang1.paygateway.channel.TransferSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Transfer;
import io.github.lianghang1.paygateway.model.TransferState;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * 微信商家转账处理器：单笔转账映射为单明细批次（outBatchNo = 网关转账单号）。
 *
 * <p>{@code payeeAccount} 承载收款用户 openid；{@code payeeName} 为收款人姓名
 * （需要实名校验时必填）。批次状态经查询接口获取，终态由回调或补单确认。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatTransferHandler implements TransferHandler {

    private final WechatClientHolder holder;

    public WechatTransferHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public TransferInvocation createTransfer(Transfer transfer, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        InitiateBatchTransferRequest request = new InitiateBatchTransferRequest();
        request.setAppid(params.getAppId());
        request.setOutBatchNo(transfer.getTransferId());
        request.setBatchName(transfer.getRemark() == null ? "转账" : transfer.getRemark());
        request.setBatchRemark(transfer.getRemark() == null ? "转账" : transfer.getRemark());
        request.setTotalAmount(transfer.getAmount());
        request.setTotalNum(1);
        TransferDetailInput detail = new TransferDetailInput();
        detail.setOutDetailNo(transfer.getTransferId());
        detail.setTransferAmount(transfer.getAmount());
        detail.setTransferRemark(transfer.getRemark() == null ? "转账" : transfer.getRemark());
        detail.setOpenid(transfer.getPayeeAccount());
        detail.setUserName(transfer.getPayeeName());
        request.setTransferDetailList(Collections.singletonList(detail));
        request.setNotifyUrl(params.getNotifyUrl());

        InitiateBatchTransferResponse response = wrap("转账", () -> holder.clients(config).transferBatchService().initiateBatchTransfer(request));
        log.info("微信转账已发起: transferId={}, batchId={}, batchStatus={}",
                transfer.getTransferId(), response.getBatchId(), response.getBatchStatus());
        return new TransferInvocation()
                .setChannelTransferNo(response.getBatchId())
                .setState(TransferState.PROCESSING);
    }

    @Override
    public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
        GetTransferBatchByNoRequest request = new GetTransferBatchByNoRequest();
        request.setBatchId(transfer.getChannelTransferNo());
        request.setNeedQueryDetail(true);
        request.setOffset(0);
        request.setLimit(1);
        TransferBatchEntity entity = wrap("转账查单", () -> holder.clients(config).transferBatchService().getTransferBatchByNo(request));
        String batchStatus = entity.getTransferBatch() == null ? null : entity.getTransferBatch().getBatchStatus();
        TransferState state = WechatStateTranslator.toTransferState(batchStatus);
        // 单明细批次：批次未产生明细视为仍在处理
        if (state == TransferState.SUCCEEDED
                && (entity.getTransferDetailList() == null || entity.getTransferDetailList().isEmpty())) {
            state = TransferState.PROCESSING;
        }
        return new TransferSnapshot()
                .setChannelTransferNo(entity.getTransferBatch() == null ? null : entity.getTransferBatch().getBatchId())
                .setState(state)
                .setRawMsg(batchStatus);
    }

    private <T> T wrap(String action, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
