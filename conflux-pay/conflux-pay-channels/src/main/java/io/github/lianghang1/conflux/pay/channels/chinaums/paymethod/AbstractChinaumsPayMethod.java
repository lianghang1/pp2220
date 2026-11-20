package io.github.lianghang1.conflux.pay.channels.chinaums.paymethod;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channels.chinaums.ChinaumsClient;
import io.github.lianghang1.conflux.pay.channels.chinaums.ChinaumsParams;
import io.github.lianghang1.conflux.pay.channels.chinaums.ChinaumsStateTranslator;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天满下单抽象基类：封装公共报文组装与应答归一，子类只声明差异维度。
 *
 * <p>差异维度：{@link #endpoint()} 下单端点、{@link #instMid()} 业务类型、
 * {@link #tradeType()} 交易类型（可空）、{@link #needSubAppId()} 是否微信渠道需 subAppId、
 * {@link #payloadField()} 从应答提取的支付要素字段、{@link #payloadType()} 唤起类型。</p>
 *
 * @author lianghang
 */
public abstract class AbstractChinaumsPayMethod implements ChinaumsPayMethod {

    /** 渠道下单端点（相对网关前缀）。 */
    protected abstract String endpoint();

    /** 业务类型 instMid。 */
    protected abstract String instMid();

    /** 交易类型 tradeType（微信 APP/MINI/JSAPI 需要；无则 null）。 */
    protected String tradeType() {
        return null;
    }

    /** 是否上送 subAppId（微信渠道需要）。 */
    protected boolean needSubAppId() {
        return false;
    }

    /** 应答中提取支付要素的字段名（如 appPayRequest/miniPayRequest/jsPayRequest/billQRCode）。 */
    protected abstract String payloadField();

    /** 支付要素映射到哪种唤起参数。 */
    protected abstract PayloadKind payloadKind();

    /** 唤起类型枚举。 */
    protected enum PayloadKind {
        APP, JSAPI, REDIRECT, QRCODE
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, ChinaumsClient client) {
        ChinaumsParams params = ChinaumsClient.paramsOf(config);
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", payment.getBizOrderNo());
        business.put("totalAmount", payment.getAmount());
        business.put("instMid", instMid());
        String notifyUrl = StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl());
        if (StringUtils.isNotBlank(notifyUrl)) {
            business.put("notifyUrl", notifyUrl);
        }
        if (StringUtils.isNotBlank(payment.getSubject())) {
            business.put("orderDesc", payment.getSubject());
        }
        if (StringUtils.isNotBlank(payment.getClientIp())) {
            business.put("clientIp", payment.getClientIp());
        }
        if (StringUtils.isNotBlank(tradeType())) {
            business.put("tradeType", tradeType());
        }
        if (needSubAppId() && StringUtils.isNotBlank(params.getSubAppId())) {
            business.put("subAppId", params.getSubAppId());
        }
        fillExtra(payment, config, business);

        JsonNode root = client.execute(method() + " 下单", endpoint(), config, business);
        String channelOrderNo = firstText(root, "targetOrderId", "merOrderId");
        String payloadContent = extractPayload(root);
        PayPayload payload = toPayload(payloadContent);
        PaymentInvocation invocation = new PaymentInvocation()
                .setState(PaymentState.PAYING)
                .setChannelOrderNo(channelOrderNo);
        if (payload != null) {
            invocation.setPayload(payload);
        }
        return invocation;
    }

    /** 子类可覆写以追加方式特有字段。 */
    protected void fillExtra(Payment payment, ChannelConfig config, Map<String, Object> business) {
    }

    /** 从应答提取支付要素：优先指定字段，缺失时回退 prepayId/targetOrderId。 */
    protected String extractPayload(JsonNode root) {
        String value = ChinaumsClient.text(root, payloadField());
        if (StringUtils.isBlank(value)) {
            value = ChinaumsClient.text(root, "prepayId");
        }
        if (StringUtils.isBlank(value)) {
            value = ChinaumsClient.text(root, "targetOrderId");
        }
        return value;
    }

    /** 把支付要素包装成对应唤起参数。 */
    protected PayPayload toPayload(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        switch (payloadKind()) {
            case APP:
                return PayPayload.app(content);
            case JSAPI:
                return PayPayload.jsapi(content);
            case REDIRECT:
                return PayPayload.redirect(content);
            case QRCODE:
                return PayPayload.qrCode(content);
            default:
                return null;
        }
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
