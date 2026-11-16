package io.github.lianghang1.conflux.pay.channels.alipay;

import com.alipay.api.internal.util.AlipaySignature;
import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.channel.CallbackConfigLookup;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.BizOrderType;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝异步通知处理器：RSA2 验签（支付宝公钥）→ trade_status 判定 → 归一化支付事件。
 *
 * <p>支付宝异步通知为表单参数（application/x-www-form-urlencoded），从
 * ChannelCallbackRequest.formParams 取参验签；应答为纯文本 {@code success}。
 * 退款为同步接口无异步通知；转账/分账通知需开放平台另行订阅，未在 v1 覆盖。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayCallbackHandler implements ChannelCallbackHandler {

    private static final DateTimeFormatter NOTIFY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayClientHolder holder;

    public AlipayCallbackHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup) {
        Map<String, String> params = raw.getFormParams() == null || raw.getFormParams().isEmpty()
                ? raw.getQueryParams() : raw.getFormParams();
        if (params == null || params.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "支付宝通知缺少表单参数");
        }
        String appId = params.get("app_id");
        if (StringUtils.isBlank(appId)) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "支付宝通知缺少 app_id");
        }
        ChannelConfig config = configLookup.byAccountKey(ChannelAccountKey.resolve(raw, appId));
        AlipayParams alipayParams = AlipayClientHolder.paramsOf(config);

        boolean verified = wrapVerify(params, alipayParams);
        if (!verified) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "支付宝通知验签失败");
        }

        // 支付宝转账通知（fund_trans）：通知体含 biz_content（内嵌 out_biz_no/status），
        // 与支付通知（trade_status 顶层字段）互斥，据此归类
        String bizContent = params.get("biz_content");
        if (StringUtils.isNotBlank(bizContent) && params.get("trade_status") == null) {
            Map<String, Object> biz = JsonUtils.toMap(bizContent);
            String status = biz.get("status") == null ? null : String.valueOf(biz.get("status"));
            String outBizNo = biz.get("out_biz_no") == null ? null : String.valueOf(biz.get("out_biz_no"));
            return new CallbackEvent()
                    .setType(BizOrderType.TRANSFER)
                    .setAppId(config.getAppId())
                    .setBizOrderNo(outBizNo)
                    .setChannelOrderNo(biz.get("order_id") == null
                            ? null : String.valueOf(biz.get("order_id")))
                    .setSuccess("SUCCESS".equals(status))
                    .setRaw(flatten(params));
        }

        CallbackEvent event = new CallbackEvent()
                .setType(BizOrderType.PAYMENT)
                .setAppId(config.getAppId())
                .setBizOrderNo(params.get("out_trade_no"))
                .setChannelOrderNo(params.get("trade_no"))
                .setSuccess("TRADE_SUCCESS".equals(params.get("trade_status"))
                        || "TRADE_FINISHED".equals(params.get("trade_status")))
                .setAmount(AlipayStateTranslator.toFen(params.get("total_amount")))
                .setOccurTime(parseTime(params.get("gmt_payment")))
                .setRaw(params);
        return event;
    }

    private Map<String, String> flatten(Map<String, String> source) {
        return new java.util.HashMap<String, String>(source);
    }

    private boolean wrapVerify(Map<String, String> params, AlipayParams alipayParams) {
        try {
            return AlipaySignature.rsaCheckV1(params, alipayParams.getAlipayPublicKey(),
                    "UTF-8", alipayParams.getSignType());
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.SIGN_INVALID,
                    "支付宝通知验签异常: {}", e.getMessage(), e);
        }
    }

    private LocalDateTime parseTime(String time) {
        if (StringUtils.isBlank(time)) {
            return null;
        }
        try {
            return LocalDateTime.parse(time, NOTIFY_TIME);
        } catch (Exception e) {
            log.debug("支付宝回调时间解析失败，按 null 处理，原值: {}", time, e);
            return null;
        }
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
