package io.github.lianghang1.paygateway.channels.wechat;

import com.github.binarywang.wxpay.bean.entpay.EntPayRequest;
import com.github.binarywang.wxpay.bean.entpay.EntPayResult;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateRequest;
import com.github.binarywang.wxpay.bean.merchanttransfer.TransferCreateResult;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.TransferHandler;
import io.github.lianghang1.paygateway.channel.TransferInvocation;
import io.github.lianghang1.paygateway.channel.TransferSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Transfer;
import io.github.lianghang1.paygateway.model.TransferState;
import io.github.lianghang1.paygateway.util.MaskUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * 微信转账处理器：V3 商家转账（MerchantTransferService.createTransfer，
 * 单明细批次）与 V2 企业付款到零钱（EntPayService.entPay），按 wxApiVersion 路由。
 *
 * <p>{@code payeeAccount} 为收款用户 openid；{@code payeeName} 为收款人实名
 * （V2 实名校验 FORCE_CHECK 时必填）。V3 受理返回批次号（PROCESSING），
 * V2 企业付款同步返回付款单号（SUCCEEDED 语义，终态以查询/回调确认）。</p>
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
            request.setPartnerTradeNo(transfer.getTransferId());
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
                    .setState(TransferState.PROCESSING);
        }
        TransferCreateRequest request = new TransferCreateRequest();
        request.setAppid(params.getAppId());
        request.setOutBatchNo(transfer.getTransferId());
        request.setBatchName(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setBatchRemark(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setTotalAmount(transfer.getAmount().intValue());
        request.setTotalNum(1);
        TransferCreateRequest.TransferDetailList detail = new TransferCreateRequest.TransferDetailList();
        detail.setOutDetailNo(transfer.getTransferId());
        detail.setTransferAmount(transfer.getAmount().intValue());
        detail.setOpenid(transfer.getPayeeAccount());
        detail.setUserName(transfer.getPayeeName());
        detail.setTransferRemark(StringUtils.defaultIfBlank(transfer.getRemark(), "转账"));
        request.setTransferDetailList(Collections.singletonList(detail));
        TransferCreateResult result = wrap("V3 商家转账", () ->
                service.getMerchantTransferService().createTransfer(request));
        log.info("微信商家转账已受理: transferId={}, payee={}, batchId={}",
                transfer.getTransferId(), MaskUtils.custom(transfer.getPayeeAccount(), 3, 2),
                result.getBatchId());
        return new TransferInvocation()
                .setChannelTransferNo(result.getBatchId())
                .setState(TransferState.PROCESSING);
    }

    @Override
    public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
        throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "微信转账终态以回调/微信商户平台为准，暂不提供独立查询");
    }

    private <T> T wrap(String action, WechatCall<T> call) {
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
