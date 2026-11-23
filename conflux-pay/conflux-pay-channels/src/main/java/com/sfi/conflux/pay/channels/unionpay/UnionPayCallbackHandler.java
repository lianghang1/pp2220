package com.sfi.conflux.pay.channels.unionpay;

import com.sfi.conflux.pay.api.CallbackEvent;
import com.sfi.conflux.pay.api.ChannelCallbackRequest;
import com.sfi.conflux.pay.channel.ChannelCallbackHandler;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.BizOrderType;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

import java.util.Map;

/**
 * 云闪付异步通知处理器：报文 JSON → 平台公钥验签 → 按金额字段构成归类事件。
 *
 * <p>通知报文以 {@code orderNo} 承载商户订单号（支付/退款分别对应各自单号）、
 * {@code txnAmt} 承载金额（分）、{@code transIndex} 承载渠道流水号；
 * 验签失败抛签名异常由使用方以失败应答触发渠道重推。应答为 {@code success}。</p>
 *
 * @author lianghang
 */
public class UnionPayCallbackHandler implements ChannelCallbackHandler {

    private final UnionPayClient client;

    public UnionPayCallbackHandler(UnionPayClient client) {
        this.client = client;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, ChannelConfig config) {
        Map<String, Object> params = JsonUtils.toMap(raw.getBody());
        if (params.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "云闪付通知报文为空");
        }
        UnionPayParams paramsConf = UnionPayClient.paramsOf(config);
        if (!client.signer().verify(params, paramsConf)) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "云闪付通知验签失败");
        }

        boolean refundNotice = StringUtils.isNotBlank(text(params.get("origOrderNo")))
                && params.get("origOrderNo") != null
                && !String.valueOf(params.get("origOrderNo")).equals(text(params.get("orderNo")))
                && text(params.get("txnType")) != null;
        boolean refund = raw.getQueryParams() != null
                && "refund".equals(raw.getQueryParams().get("noticeType"));
        CallbackEvent event = new CallbackEvent()
                .setType(refund || refundNotice ? BizOrderType.REFUND : BizOrderType.PAYMENT)
                .setConfigId(config.getConfigId())
                .setBizOrderNo(text(params.get("orderNo")))
                .setChannelOrderNo(text(params.get("transIndex")))
                .setSuccess("00".equals(text(params.get("respCode")))
                        || "00".equals(text(params.get("origRespCode"))));
        String amountText = text(params.get("txnAmt"));
        if (StringUtils.isNotBlank(amountText)) {
            try {
                event.setAmount(Long.valueOf(amountText.trim()));
            } catch (NumberFormatException e) {
                // 金额字段非数字时交由订单比对兜底
            }
        }
        event.setRaw(toFlatTextMap(params));
        return event;
    }

    private Map<String, String> toFlatTextMap(Map<String, Object> source) {
        Map<String, String> flat = new java.util.HashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            flat.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return flat;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public String successReply() {
        return "success";
    }

    @Override
    public String failureReply() {
        return "fail";
    }
}
