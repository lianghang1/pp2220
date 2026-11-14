package io.github.lianghang1.conflux.pay.channels.paypal;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.TransferHandler;
import io.github.lianghang1.conflux.pay.channel.TransferInvocation;
import io.github.lianghang1.conflux.pay.channel.TransferSnapshot;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPal 转账处理器：Payouts 批量付款（单笔映射为单条目批次，
 * sender_batch_id = 网关转账单号）。
 *
 * <p>{@code payeeAccount} 承载收款 PayPal 账户（EMAIL 方式为邮箱）；
 * {@code remark} 为付款附言。批次受理返回 payout_batch_id，终态经查询确认。</p>
 *
 * @author lianghang
 */
public class PayPalTransferHandler implements TransferHandler {

    private final PayPalClient client;

    public PayPalTransferHandler(PayPalClient client) {
        this.client = client;
    }

    @Override
    public TransferInvocation createTransfer(Transfer transfer, ChannelConfig config) {
        Map<String, Object> body = new HashMap<String, Object>();
        Map<String, Object> header = new HashMap<String, Object>();
        header.put("sender_batch_id", transfer.getTransferId());
        header.put("email_subject", StringUtils.defaultIfBlank(transfer.getRemark(), "You have a payout"));
        body.put("sender_batch_header", header);
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("recipient_type", "EMAIL");
        Map<String, Object> amount = new HashMap<String, Object>();
        amount.put("currency_code", transfer.getCurrency());
        amount.put("value", PayPalTranslator.toYuan(transfer.getAmount()));
        item.put("amount", amount);
        item.put("receiver", transfer.getPayeeAccount());
        item.put("note", StringUtils.defaultIfBlank(transfer.getRemark(), "payout"));
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item);
        body.put("items", items);

        Map<String, Object> result = client.execute("转账", config, "POST", "/v1/payments/payouts", body);
        Map<String, Object> batchHeader = result.get("batch_header") instanceof Map
                ? (Map<String, Object>) result.get("batch_header") : null;
        String batchId = batchHeader == null ? null : PayPalTranslator.text(batchHeader.get("payout_batch_id"));
        String batchStatus = batchHeader == null ? null : PayPalTranslator.text(batchHeader.get("batch_status"));
        return new TransferInvocation()
                .setChannelTransferNo(batchId)
                .setState(PayPalTranslator.toTransferState(batchStatus) == io.github.lianghang1.conflux.pay.model.TransferState.SUCCEEDED
                        ? io.github.lianghang1.conflux.pay.model.TransferState.PROCESSING
                        : PayPalTranslator.toTransferState(batchStatus));
    }

    @Override
    public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
        String batchId = transfer.getChannelTransferNo();
        if (StringUtils.isBlank(batchId)) {
            return new TransferSnapshot().setState(io.github.lianghang1.conflux.pay.model.TransferState.PROCESSING)
                    .setRawMsg("no payout batch id");
        }
        Map<String, Object> result = client.execute("转账查单", config, "GET",
                "/v1/payments/payouts/" + batchId, null);
        Map<String, Object> batchHeader = result.get("batch_header") instanceof Map
                ? (Map<String, Object>) result.get("batch_header") : null;
        String status = batchHeader == null ? null : PayPalTranslator.text(batchHeader.get("batch_status"));
        return new TransferSnapshot()
                .setChannelTransferNo(batchId)
                .setState(PayPalTranslator.toTransferState(status))
                .setRawMsg(status);
    }
}
