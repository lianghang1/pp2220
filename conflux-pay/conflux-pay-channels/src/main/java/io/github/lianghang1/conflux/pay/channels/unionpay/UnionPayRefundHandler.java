package io.github.lianghang1.conflux.pay.channels.unionpay;

import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.RefundHandler;
import io.github.lianghang1.conflux.pay.channel.RefundInvocation;
import io.github.lianghang1.conflux.pay.channel.RefundSnapshot;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 云闪付退款处理器：按退款单号（orderNo = 网关退款单号）发起退款，
 * 关联原交易（origOrderNo/origTxnAmt），退款结果经查询接口或回调确认。
 *
 * @author lianghang
 */
public class UnionPayRefundHandler implements RefundHandler {

    private final UnionPayClient client;

    public UnionPayRefundHandler(UnionPayClient client) {
        this.client = client;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        Map<String, Object> requestParams = new HashMap<String, Object>();
        requestParams.put("orderNo", refund.getBizRefundNo());
        requestParams.put("origOrderNo", payment.getBizOrderNo());
        requestParams.put("origTxnAmt", payment.getAmount());
        requestParams.put("txnAmt", refund.getAmount());
        requestParams.put("currencyCode", "156");
        requestParams.put("orderInfo", "refund");
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        Map<String, Object> result = client.execute("退款", "/gateway/api/pay/refund", config, requestParams);
        return new RefundInvocation()
                .setChannelRefundNo(text(result.get("transIndex")))
                .setState(RefundState.PROCESSING);
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        Map<String, Object> requestParams = new HashMap<String, Object>();
        requestParams.put("orderNo", refund.getBizRefundNo());
        requestParams.put("origOrderNo", refund.getBizOrderNo());
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        Map<String, Object> result = client.execute("退款查单", "/gateway/api/pay/refundQuery", config, requestParams);
        String origRespCode = text(result.get("origRespCode"));
        RefundState state = "00".equals(origRespCode) ? RefundState.SUCCEEDED
                : ("02".equals(origRespCode) || "05".equals(origRespCode))
                ? RefundState.PROCESSING : RefundState.FAILED;
        return new RefundSnapshot()
                .setChannelRefundNo(text(result.get("transIndex")))
                .setState(state)
                .setRawMsg(origRespCode);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
