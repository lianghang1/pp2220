package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.RefundHandler;
import io.github.lianghang1.paygateway.channel.RefundInvocation;
import io.github.lianghang1.paygateway.channel.RefundSnapshot;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.Refund;
import io.github.lianghang1.paygateway.model.RefundState;
import io.github.lianghang1.paygateway.util.StringUtils;
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
        model.setOutTradeNo(payment.getPaymentId());
        model.setRefundAmount(AlipayStateTranslator.toYuan(refund.getAmount()));
        model.setOutRequestNo(refund.getRefundId());
        model.setRefundReason(StringUtils.isNotBlank(refund.getReason()) ? refund.getReason() : "退款");
        request.setBizModel(model);
        AlipayTradeRefundResponse response = holder.execute("退款", config, client -> client.execute(request));
        return new RefundInvocation()
                .setChannelRefundNo(response.getTradeNo())
                .setState(AlipayStateTranslator.toRefundState(response.getFundChange()));
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutRequestNo(refund.getRefundId());
        request.setBizModel(model);
        AlipayTradeFastpayRefundQueryResponse response =
                holder.execute("退款查单", config, client -> client.execute(request));
        if (!response.isSuccess() && StringUtils.isNotBlank(response.getSubCode())) {
            // 查询无记录视为仍在处理（等待受理）
            return new RefundSnapshot()
                    .setState(RefundState.PROCESSING)
                    .setRawMsg(response.getSubCode());
        }
        return new RefundSnapshot()
                .setChannelRefundNo(response.getTradeNo())
                .setState(RefundState.SUCCEEDED)
                .setRawMsg(response.getRefundStatus());
    }
}
