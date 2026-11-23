package com.sfi.conflux.pay.channels.chinaums;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天满退款处理器：发起退款、查询退款。
 *
 * <p>退款金额单位分；merOrderId 为原支付商户订单号；refundOrderId 为商户退款单号。
 * 成功判定 errCode=SUCCESS 且 refundStatus=SUCCESS。</p>
 *
 * @author lianghang
 */
public class ChinaumsRefundHandler implements RefundHandler {

    private final ChinaumsClient client;

    public ChinaumsRefundHandler(ChinaumsClient client) {
        this.client = client;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", payment.getBizOrderNo());
        business.put("refundAmount", refund.getAmount());
        business.put("refundOrderId", refund.getBizRefundNo());
        if (StringUtils.isNotBlank(payment.getChannelOrderNo())) {
            business.put("targetOrderId", payment.getChannelOrderNo());
        }
        if (StringUtils.isNotBlank(refund.getReason())) {
            business.put("refundDesc", refund.getReason());
        }
        JsonNode root = client.execute("退款", ChinaumsClient.API_REFUND, config, business);
        String refundStatus = ChinaumsClient.text(root, "refundStatus");
        RefundState state = ChinaumsStateTranslator.toRefundState(refundStatus, ChinaumsClient.text(root, "status"));
        return new RefundInvocation()
                .setChannelRefundNo(firstText(root, "refundOrderId", "refundTargetOrderId"))
                .setState(state);
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", refund.getBizOrderNo());
        if (StringUtils.isNotBlank(refund.getChannelRefundNo())) {
            business.put("refundOrderId", refund.getChannelRefundNo());
        }
        JsonNode root = client.execute("退款查询", ChinaumsClient.API_REFUND_QUERY, config, business);
        String refundStatus = ChinaumsClient.text(root, "refundStatus");
        return new RefundSnapshot()
                .setChannelRefundNo(firstText(root, "refundOrderId", "refundTargetOrderId"))
                .setState(ChinaumsStateTranslator.toRefundState(refundStatus, ChinaumsClient.text(root, "status")))
                .setSucceedTime(ChinaumsStateTranslator.toLocalDateTime(ChinaumsClient.text(root, "refundPayTime")))
                .setRawMsg(refundStatus);
    }

    private static String firstText(JsonNode root, String... fields) {
        for (String f : fields) {
            String v = ChinaumsClient.text(root, f);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
}
