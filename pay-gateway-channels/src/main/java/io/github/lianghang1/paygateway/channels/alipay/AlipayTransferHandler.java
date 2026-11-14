package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.domain.AlipayFundTransUniTransferModel;
import com.alipay.api.domain.Participant;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.TransferHandler;
import io.github.lianghang1.paygateway.channel.TransferInvocation;
import io.github.lianghang1.paygateway.channel.TransferSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Transfer;
import io.github.lianghang1.paygateway.util.MaskUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付宝单笔转账处理器：到支付宝账户（免密转账产品 TRANS_ACCOUNT_NO_PWD）。
 *
 * <p>{@code payeeAccount} 为支付宝登录号（手机号/邮箱，经 MaskUtils 打码落日志）；
 * {@code payeeName} 为收款人实名（需要实名校验时必填）。受理结果同步返回，
 * DEALING 状态等待查询确认。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayTransferHandler implements TransferHandler {

    private final AlipayClientHolder holder;

    public AlipayTransferHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public TransferInvocation createTransfer(Transfer transfer, ChannelConfig config) {
        AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
        AlipayFundTransUniTransferModel model = new AlipayFundTransUniTransferModel();
        model.setOutBizNo(transfer.getTransferId());
        model.setTransAmount(AlipayStateTranslator.toYuan(transfer.getAmount()));
        model.setProductCode("TRANS_ACCOUNT_NO_PWD");
        model.setBizScene("DIRECT_TRANSFER");
        model.setOrderTitle(transfer.getRemark() == null ? "转账" : transfer.getRemark());
        model.setRemark(transfer.getRemark());
        Participant payee = new Participant();
        payee.setIdentity(transfer.getPayeeAccount());
        payee.setIdentityType("ALIPAY_LOGON_ID");
        payee.setName(transfer.getPayeeName());
        model.setPayeeInfo(payee);
        request.setNotifyUrl(AlipayClientHolder.paramsOf(config).getNotifyUrl());
        request.setBizModel(model);
        AlipayFundTransUniTransferResponse response =
                holder.execute("转账", config, (client, authToken) -> client.execute(request, authToken));
        log.info("支付宝转账已受理: transferId={}, payee={}, status={}",
                transfer.getTransferId(), MaskUtils.custom(transfer.getPayeeAccount(), 3, 2), response.getStatus());
        return new TransferInvocation()
                .setChannelTransferNo(response.getOrderId())
                .setState(AlipayStateTranslator.toTransferState(response.getStatus()));
    }

    @Override
    public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
        com.alipay.api.request.AlipayFundTransCommonQueryRequest request =
                new com.alipay.api.request.AlipayFundTransCommonQueryRequest();
        com.alipay.api.domain.AlipayFundTransCommonQueryModel model =
                new com.alipay.api.domain.AlipayFundTransCommonQueryModel();
        model.setOutBizNo(transfer.getTransferId());
        model.setProductCode("TRANS_ACCOUNT_NO_PWD");
        request.setBizModel(model);
        com.alipay.api.response.AlipayFundTransCommonQueryResponse response =
                holder.execute("转账查单", config, (client, authToken) -> client.execute(request, authToken));
        return new TransferSnapshot()
                .setChannelTransferNo(response.getOrderId())
                .setState(AlipayStateTranslator.toTransferState(response.getStatus()))
                .setRawMsg(response.getStatus());
    }
}
