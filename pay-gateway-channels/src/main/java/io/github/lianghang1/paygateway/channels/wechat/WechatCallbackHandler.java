package io.github.lianghang1.paygateway.channels.wechat;

import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;

import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyResult;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.paygateway.api.CallbackEvent;
import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;
import io.github.lianghang1.paygateway.channel.CallbackConfigLookup;
import io.github.lianghang1.paygateway.channel.ChannelCallbackHandler;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.BizOrderType;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信异步通知处理器：V2（XML，MD5 验签）与 V3（JSON，平台证书验签 +
 * AES-GCM 解密）双版本自动识别，支付/退款通知归类为统一事件。
 *
 * <p>版本识别按报文形态（XML=V2、JSON=V3）；V3 报文先按支付通知解密，
 * trade_state 缺失时回退退款通知解析。appId 由回调地址查询参数携带
 * （约定 {@code ?appId=...}）。应答报文为微信认可的应答结构。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatCallbackHandler implements ChannelCallbackHandler {

    private final WechatClientHolder holder;

    public WechatCallbackHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup) {
        String body = raw.getBody();
        if (StringUtils.isBlank(body)) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "微信通知报文为空");
        }
        String appId = raw.getQueryParams() == null ? null : raw.getQueryParams().get("appId");
        if (StringUtils.isBlank(appId)) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信回调地址需携带 appId 查询参数以定位渠道配置");
        }
        ChannelConfig config = configLookup.byChannelApp(appId);
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);

        if (body.trim().startsWith("<")) {
            return parseV2(raw, config, service);
        }
        return parseV3(raw, config, service);
    }

    /** V2 XML 通知：WxJava 验签（MD5）后映射事件。 */
    private CallbackEvent parseV2(ChannelCallbackRequest raw, ChannelConfig config, WxPayService service) {
        String body = raw.getBody();
        if (body.contains("refund_status")) {
            WxPayRefundNotifyResult result = wrap("V2 退款通知", () -> service.parseRefundNotifyResult(body));
            WxPayRefundNotifyResult.ReqInfo reqInfo = result.getReqInfo();
            return new CallbackEvent()
                    .setType(BizOrderType.REFUND)
                    .setAppId(config.getAppId())
                    .setBizOrderNo(reqInfo.getOutRefundNo())
                    .setChannelOrderNo(reqInfo.getRefundId())
                    .setSuccess("SUCCESS".equals(reqInfo.getRefundStatus()))
                    .setOccurTime(WechatStateTranslator.toLocalDateTime(reqInfo.getSuccessTime()))
                    .setRaw(flatten(JsonUtils.toMap(JsonUtils.toJson(reqInfo))));
        }
        WxPayOrderNotifyResult result = wrap("V2 支付通知", () -> service.parseOrderNotifyResult(body));
        CallbackEvent event = new CallbackEvent()
                .setType(BizOrderType.PAYMENT)
                .setAppId(config.getAppId())
                .setBizOrderNo(result.getOutTradeNo())
                .setChannelOrderNo(result.getTransactionId())
                .setSuccess("SUCCESS".equals(result.getResultCode()))
                .setOccurTime(WechatStateTranslator.toLocalDateTime(result.getTimeEnd()))
                .setRaw(flatten(JsonUtils.toMap(JsonUtils.toJson(result))));
        if (result.getTotalFee() != null) {
            event.setAmount(result.getTotalFee().longValue());
        }
        return event;
    }

    /** V3 JSON 通知：平台证书验签 + AES-GCM 解密（先支付后退款回退解析）。 */
    private CallbackEvent parseV3(ChannelCallbackRequest raw, ChannelConfig config, WxPayService service) {
        SignatureHeader header = new SignatureHeader();
        Map<String, String> headers = raw.getHeaders() == null ? new HashMap<String, String>() : raw.getHeaders();
        header.setSerial(headers.get("Wechatpay-Serial"));
        header.setNonce(headers.get("Wechatpay-Nonce"));
        header.setSignature(headers.get("Wechatpay-Signature"));
        header.setTimeStamp(headers.get("Wechatpay-Timestamp"));
        if (StringUtils.isBlank(header.getSerial()) || StringUtils.isBlank(header.getNonce())
                || StringUtils.isBlank(header.getSignature()) || StringUtils.isBlank(header.getTimeStamp())) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "缺少微信 V3 回调签名头");
        }
        WxPayNotifyV3Result paymentNotify = wrap("V3 支付通知", () ->
                service.parseOrderNotifyV3Result(raw.getBody(), header));
        WxPayNotifyV3Result.DecryptNotifyResult paymentResult = paymentNotify.getResult();
        if (paymentResult != null && StringUtils.isNotBlank(paymentResult.getTradeState())) {
            CallbackEvent event = new CallbackEvent()
                    .setType(BizOrderType.PAYMENT)
                    .setAppId(config.getAppId())
                    .setBizOrderNo(paymentResult.getOutTradeNo())
                    .setChannelOrderNo(paymentResult.getTransactionId())
                    .setSuccess("SUCCESS".equals(paymentResult.getTradeState()))
                    .setOccurTime(WechatStateTranslator.toLocalDateTime(paymentResult.getSuccessTime()))
                    .setRaw(flatten(JsonUtils.toMap(JsonUtils.toJson(paymentResult))));
            if (paymentResult.getAmount() != null && paymentResult.getAmount().getTotal() != null) {
                event.setAmount(paymentResult.getAmount().getTotal().longValue());
            }
            return event;
        }
        WxPayRefundNotifyV3Result refundNotify = wrap("V3 退款通知", () ->
                service.parseRefundNotifyV3Result(raw.getBody(), header));
        WxPayRefundNotifyV3Result.DecryptNotifyResult refundResult = refundNotify.getResult();
        return new CallbackEvent()
                .setType(BizOrderType.REFUND)
                .setAppId(config.getAppId())
                .setBizOrderNo(refundResult.getOutRefundNo())
                .setChannelOrderNo(refundResult.getRefundId())
                .setSuccess("SUCCESS".equals(refundResult.getRefundStatus()))
                .setOccurTime(WechatStateTranslator.toLocalDateTime(refundResult.getSuccessTime()))
                .setRaw(flatten(JsonUtils.toMap(JsonUtils.toJson(refundResult))));
    }

    private Map<String, String> flatten(Map<String, Object> source) {
        Map<String, String> flat = new HashMap<String, String>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            flat.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return flat;
    }

    private <T> T wrap(String action, WechatCall<T> call) {
        try {
            return call.call();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID,
                    "微信{}验签/解析失败: {}", action, e.getMessage(), e);
        }
    }

    @Override
    public String successReply() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    @Override
    public String failureReply() {
        return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
    }
}
