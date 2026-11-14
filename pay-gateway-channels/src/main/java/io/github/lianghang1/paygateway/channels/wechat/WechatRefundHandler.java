package io.github.lianghang1.paygateway.channels.wechat;

import com.github.binarywang.wxpay.bean.request.WxPayRefundQueryV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayRefundRequest;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayRefundResult;
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.RefundHandler;
import io.github.lianghang1.paygateway.channel.RefundInvocation;
import io.github.lianghang1.paygateway.channel.RefundSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.Refund;
import io.github.lianghang1.paygateway.model.RefundState;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信退款处理器：V3（refundV3）与 V2（refund）双路径，按渠道参数的
 * wxApiVersion 路由；服务商模式经 WxJava 配置透传。
 *
 * <p>V2 发起后状态以退款回调/退款查询确认（微信 V2 同步响应不含终态标识）；
 * V3 同步返回受理状态。金额单位统一为分。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatRefundHandler implements RefundHandler {

    private final WechatClientHolder holder;

    public WechatRefundHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        if (params.isV2()) {
            WxPayRefundRequest request = new WxPayRefundRequest();
            request.setOutTradeNo(payment.getPaymentId());
            request.setOutRefundNo(refund.getRefundId());
            request.setTotalFee(payment.getAmount().intValue());
            request.setRefundFee(refund.getAmount().intValue());
            request.setNotifyUrl(params.getNotifyUrl());
            WxPayRefundResult result = wrap("V2 退款", () -> service.refund(request));
            return new RefundInvocation()
                    .setChannelRefundNo(result.getRefundId())
                    .setState(RefundState.PROCESSING);
        }
        WxPayRefundV3Request request = new WxPayRefundV3Request();
        request.setOutTradeNo(payment.getPaymentId());
        request.setOutRefundNo(refund.getRefundId());
        request.setReason(StringUtils.defaultIfBlank(refund.getReason(), "退款"));
        request.setNotifyUrl(params.getNotifyUrl());
        WxPayRefundV3Request.Amount amount = new WxPayRefundV3Request.Amount();
        amount.setRefund(refund.getAmount().intValue());
        amount.setTotal(payment.getAmount().intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        WxPayRefundV3Result result = wrap("V3 退款", () -> service.refundV3(request));
        return new RefundInvocation()
                .setChannelRefundNo(result.getRefundId())
                .setState(WechatStateTranslator.toRefundState(result.getStatus()));
    }

    @Override
    public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        if (params.isV2()) {
            com.github.binarywang.wxpay.bean.request.WxPayRefundQueryRequest request =
                    new com.github.binarywang.wxpay.bean.request.WxPayRefundQueryRequest();
            request.setOutRefundNo(refund.getRefundId());
            com.github.binarywang.wxpay.bean.result.WxPayRefundQueryResult result =
                    wrap("V2 退款查单", () -> service.refundQuery(request));
            RefundState state = RefundState.PROCESSING;
            String succeedTime = null;
            if (result.getRefundRecords() != null && !result.getRefundRecords().isEmpty()) {
                com.github.binarywang.wxpay.bean.result.WxPayRefundQueryResult.RefundRecord record =
                        result.getRefundRecords().get(0);
                state = WechatStateTranslator.toRefundState(record.getRefundStatus());

            }
            return new RefundSnapshot()
                    .setChannelRefundNo(refund.getRefundId())
                    .setState(state)
                    .setSucceedTime(WechatStateTranslator.toLocalDateTime(succeedTime))
                    .setRawMsg(result.getRefundRecords() == null ? null
                            : result.getRefundRecords().size() + " records");
        }
        WxPayRefundQueryV3Request request = new WxPayRefundQueryV3Request();
        request.setOutRefundNo(refund.getRefundId());
        WxPayRefundQueryV3Result result = wrap("V3 退款查单", () -> service.refundQueryV3(request));
        return new RefundSnapshot()
                .setChannelRefundNo(result.getRefundId())
                .setState(WechatStateTranslator.toRefundState(result.getStatus()))
                .setSucceedTime(WechatStateTranslator.toLocalDateTime(result.getSuccessTime()))
                .setRawMsg(result.getStatus());
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
