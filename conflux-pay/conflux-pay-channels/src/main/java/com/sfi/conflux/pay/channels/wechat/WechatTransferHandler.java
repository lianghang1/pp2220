package com.sfi.conflux.pay.channels.wechat;

import com.github.binarywang.wxpay.bean.entpay.EntPayRequest;
import com.github.binarywang.wxpay.bean.entpay.EntPayResult;
import com.github.binarywang.wxpay.bean.merchanttransfer.BatchesQueryResult;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateRequest;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateResult;
import com.github.binarywang.wxpay.bean.entpay.EntPayQueryResult;
import com.github.binarywang.wxpay.bean.merchanttransfer.WxBatchesQueryRequest;
import com.github.binarywang.wxpay.service.WxPayService;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.TransferHandler;
import com.sfi.conflux.pay.channel.TransferInvocation;
import com.sfi.conflux.pay.channel.TransferSnapshot;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.Transfer;
import com.sfi.conflux.pay.model.TransferState;
import com.sfi.conflux.common.util.MaskUtils;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信转账处理器：V3 商家转账（MerchantTransferService.createTransfer，
 * 单明细批次）与 V2 企业付款到零钱（EntPayService.entPay），按 wxApiVersion 路由。
 *
 * <p>{@code payeeAccount} 为收款用户 openid；{@code payeeName} 为收款人实名
 * （V2 实名校验 FORCE_CHECK 时必填）。V3 受理返回批次号（终态经回调或批次查询确认），
 * V2 企业付款同步返回付款单号。</p>
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
        WxPayService service = holder.service(config);
        if (params.isV2()) {
            EntPayRequest request = new EntPayRequest();
            request.setMchAppid(params.getAppId());
            request.setMchId(params.getMerchantId());
            request.setPartnerTradeNo(transfer.getBizTransferNo());
            request.setOpenid(transfer.getPayeeAccount());
            boolean withName = StringUtils.isNotBlank(transfer.getPayeeName());
            request.setCheckName(withName ? "FORCE_CHECK" : "NO_CHECK");
            request.setReUserName(transfer.getPayeeName());
            request.setAmount(transfer.getAmount().intValue());
            request.setDescription(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
            request.setSpbillCreateIp("127.0.0.1");
            EntPayResult result = wrap("V2 企业付款", () -> service.getEntPayService().entPay(request));
            return new TransferInvocation()
                    .setChannelTransferNo(result.getPaymentNo())
                    .setState(TransferState.SUCCEEDED);
        }
        TransferCreateRequest request = new TransferCreateRequest();
        request.setAppid(params.getAppId());
        request.setOutBatchNo(transfer.getBizTransferNo());
        request.setBatchName(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setBatchRemark(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setTotalAmount(transfer.getAmount().intValue());
        request.setTotalNum(1);
        TransferCreateRequest.TransferDetailList detail = new TransferCreateRequest.TransferDetailList();
        detail.setOutDetailNo(transfer.getBizTransferNo());
        detail.setTransferAmount(transfer.getAmount().intValue());
        detail.setOpenid(transfer.getPayeeAccount());
        detail.setUserName(transfer.getPayeeName());
        detail.setTransferRemark(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setTransferDetailList(java.util.Collections.singletonList(detail));
        TransferCreateResult result = wrap("V3 商家转账", () ->
                service.getMerchantTransferService().createTransfer(request));
        log.info("微信商家转账已受理: transferId={}, payee={}, batchId={}",
                transfer.getBizTransferNo(), MaskUtils.custom(transfer.getPayeeAccount(), 3, 2),
                result.getBatchId());
        return new TransferInvocation()
                .setChannelTransferNo(result.getBatchId())
                .setState(TransferState.PROCESSING);
    }

    @Override
    public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        if (params.isV2()) {
            EntPayQueryResult result = wrap("V2 企业付款查单", () ->
                    service.getEntPayService().queryEntPay(transfer.getChannelTransferNo()));
            TransferState state = "SUCCESS".equals(result.getStatus())
                    ? TransferState.SUCCEEDED : TransferState.PROCESSING;
            return new TransferSnapshot()
                    .setChannelTransferNo(result.getDetailId())
                    .setState(state)
                    .setRawMsg(result.getStatus());
        }
        WxBatchesQueryRequest request = new WxBatchesQueryRequest();
        request.setBatchId(transfer.getChannelTransferNo());
        request.setNeedQueryDetail(true);
        request.setOffset(0);
        request.setLimit(1);
        BatchesQueryResult result = wrap("V3 转账批次查询", () ->
                service.getMerchantTransferService().queryWxBatches(request));
        BatchesQueryResult.TransferBatch batch = result.getTransferBatch();
        String batchStatus = batch == null ? null : batch.getBatchStatus();
        TransferState state = WechatStateTranslator.toTransferState(batchStatus);
        return new TransferSnapshot()
                .setChannelTransferNo(batch == null ? null : batch.getBatchId())
                .setState(state)
                .setRawMsg(batchStatus);
    }

    private <T> T wrap(String action, com.sfi.conflux.pay.channels.wechat.WechatCall<T> call) {
        try {
            return call.call();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
