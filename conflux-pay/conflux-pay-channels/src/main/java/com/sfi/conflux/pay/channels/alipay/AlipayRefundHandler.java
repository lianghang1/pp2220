package com.sfi.conflux.pay.channels.alipay;

import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付宝退款处理器：同步受理（fundChange=Y 即退款成功），
 * outRequestNo 采用网关退款单号支持同一支付单多次退款。
 *
 * @author lianghang
 */
@Slf4j
public class AlipayRefundHandler implements RefundHandler {

    private final AlipayClientHolder holder;

    public AlipayRefundHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(payment.getBizOrderNo());
        model.setRefundAmount(AlipayStateTranslator.toYuan(refund.getAmount()));
        model.setOutRequestNo(refund.getBizRefundNo());
        model.setRefundReason(StringUtils.isNotBlank(refund.getReason()) ? refund.getReason() : "退款");
        request.setBizModel(model);
        AlipayTradeRefundResponse response = holder.execute("退款", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        return new RefundInvocation()
                .setChannelRefundNo(response.getTradeNo())
                .setState(AlipayStateTranslator.toRefundState(response.getFundChange()));
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutRequestNo(refund.getBizRefundNo());
        request.setBizModel(model);
        AlipayTradeFastpayRefundQueryResponse response =
                holder.executeRaw("退款查单", config, (client, authToken) -> {
            if (StringUtils.isNotBlank(authToken)) {
                request.putOtherTextParam("app_auth_token", authToken);
            }
            return client.execute(request);
        });
        if (!response.isSuccess() && StringUtils.isNotBlank(response.getSubCode())) {
            // 查询无记录视为仍在处理（等待受理）
            return new RefundSnapshot()
                    .setState(RefundState.PROCESSING)
                    .setRawMsg(response.getSubCode());
        }
        // 接口查询成功 ≠ 退款成功；须按 refund_status 判定（官方明确 10000 仅代表查询成功）
        return new RefundSnapshot()
                .setChannelRefundNo(response.getTradeNo())
                .setState(AlipayStateTranslator.toRefundStatus(response.getRefundStatus()))
                .setRawMsg(response.getRefundStatus());
    }
}
