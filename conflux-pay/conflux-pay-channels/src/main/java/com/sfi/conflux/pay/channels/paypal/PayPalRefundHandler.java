package com.sfi.conflux.pay.channels.paypal;

import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * PayPal 退款处理器：对捕获单（capture_id）发起部分/全额退款（payments v2）。
 *
 * @author lianghang
 */
public class PayPalRefundHandler implements RefundHandler {

    private final PayPalClient client;

    public PayPalRefundHandler(PayPalClient client) {
        this.client = client;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        String captureId = payment.getChannelOrderNo();
        if (StringUtils.isBlank(captureId)) {
            return new RefundInvocation().setState(RefundState.FAILED);
        }
        Map<String, Object> body = new HashMap<String, Object>();
        Map<String, Object> amount = new HashMap<String, Object>();
        amount.put("currency_code", refund.getCurrency());
        amount.put("value", PayPalTranslator.toYuan(refund.getAmount()));
        body.put("amount", amount);
        if (StringUtils.isNotBlank(refund.getReason())) {
            body.put("note_to_payer", refund.getReason());
        }
        Map<String, Object> result = client.execute("退款", config, "POST",
                "/v2/payments/captures/" + captureId + "/refund", body);
        return new RefundInvocation()
                .setChannelRefundNo(PayPalTranslator.text(result.get("id")))
                .setState(PayPalTranslator.toRefundState(PayPalTranslator.text(result.get("status"))));
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        if (StringUtils.isBlank(refund.getChannelRefundNo())) {
            return new RefundSnapshot().setState(RefundState.PROCESSING).setRawMsg("no refund id");
        }
        Map<String, Object> result = client.execute("退款查单", config, "GET",
                "/v2/payments/refunds/" + refund.getChannelRefundNo(), null);
        return new RefundSnapshot()
                .setChannelRefundNo(PayPalTranslator.text(result.get("id")))
                .setState(PayPalTranslator.toRefundState(PayPalTranslator.text(result.get("status"))))
                .setRawMsg(PayPalTranslator.text(result.get("status")));
    }
}
