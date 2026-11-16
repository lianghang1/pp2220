package io.github.lianghang1.conflux.invoice.channels.piaotong.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongParams;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 票通回调推送解析（2.13 推送发票主要信息 / 2.14 推送全票面）。
 *
 * <p>推送请求为<b>与响应相同的加密信封</b>：用平台公钥验签 → 3DES 解密 content
 * → 业务报文（taxpayerNum/invoiceReqSerialNo/invoiceType/invoiceKind/code/msg/
 * invoiceCode/invoiceNo/electronicInvoiceNo/invoiceDate/金额等）。应答同样为信封，
 * 业务部分 {@code code=0000/9999}；因 SPI 的 {@code reply(success, message)}
 * 无法拿到配置，应答信封依赖 {@link #parse} 同线程暂存的配置
 * （ThreadLocal），拿不到时退化为业务明文 JSON。</p>
 *
 * <p>状态归一：</p>
 * <ul>
 *   <li>{@code 0000} → SUCCEEDED（XML 法定原件不在推送里，由编排层调 2.15 补拉归档）；</li>
 *   <li>{@code 9999} → FAILED；</li>
 *   <li>{@code 3999} → WAITING_AUTH（challenge 携带 authenticationQrcode/authId）；</li>
 *   <li>{@code 6666/7777/4999/5999} → PROCESSING 保持挂起。</li>
 * </ul>
 *
 * @author lianghang
 */
@Slf4j
public final class PiaoTongCallbackParser {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** parse 与 reply 同线程共享配置（SPI reply 无 config 入参的桥接）。 */
    private static final ThreadLocal<PiaoTongParams> REPLY_PARAMS = new ThreadLocal<PiaoTongParams>();

    private PiaoTongCallbackParser() {
    }

    public static InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        if (raw == null || StringUtils.isBlank(raw.getBody())) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "票通回调报文为空");
        }
        PiaoTongParams params = paramsOf(config);
        // 暂存配置供 reply() 构建加密应答信封（同线程）；解析异常时立即清理，避免泄漏
        REPLY_PARAMS.set(params);
        try {
            return doParse(raw, params);
        } catch (RuntimeException e) {
            REPLY_PARAMS.remove();
            throw e;
        }
    }

    private static InvoiceCallbackEvent doParse(InvoiceCallbackRequest raw,
                                                PiaoTongParams params) {
        PiaoTongResponse response = PiaoTongEnvelope.parse(raw.getBody(), params, true);
        JsonNode business = response.getRoot();
        if (business == null || !business.isObject()) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "票通推送缺少业务报文（content 解密失败或为空）");
        }
        String serialNo = text(business, "invoiceReqSerialNo");
        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setAccountKey(accountKey(raw))
                .setChannelOrderNo(serialNo)
                .setBizOrderNo(text(business, "tradeNo"))
                .setRawPayload(response.getRawBody());

        Map<String, String> attrs = new LinkedHashMap<String, String>();
        put(attrs, "taxpayerNum", text(business, "taxpayerNum"));
        put(attrs, "invoiceKind", text(business, "invoiceKind"));
        put(attrs, "definedData", text(business, "definedData"));
        String code = text(business, "code");
        String invoiceType = text(business, "invoiceType");
        attrs.put("channelResult", nullTo(code, ""));

        event.setInvoiceNo(text(business, "invoiceNo"));
        event.setInvoiceCode(text(business, "invoiceCode"));
        put(attrs, "electronicInvoiceNo", text(business, "electronicInvoiceNo"));
        event.setInvoiceDate(parseDate(text(business, "invoiceDate")));
        event.setAmount(dec(business, "amountWithTax", "noTaxAmount"));
        event.setTaxAmount(dec(business, "taxAmount"));
        event.setErrorMsg(text(business, "msg"));
        put(attrs, "authId", text(business, "authId"));
        put(attrs, "authenticationQrcode", text(business, "authenticationQrcode"));

        boolean red = "2".equals(invoiceType);
        if (red) {
            // 红票推送：登记红票标记；确认单状态由 2.29/2.34 查询推进
            attrs.put("redInvoice", "true");
        }

        if (PiaoTongSpec.ST_SUCCESS.equals(code)) {
            event.setState(InvoiceState.SUCCEEDED);
        } else if (PiaoTongSpec.ST_FAILED.equals(code)) {
            event.setState(InvoiceState.FAILED);
            event.setErrorCode(firstNonBlank(code, "PT_ISSUE_FAILED"));
        } else if (PiaoTongSpec.ST_NEED_AUTH.equals(code)) {
            event.setState(InvoiceState.WAITING_AUTH);
            attrs.put("authType", "FACE");
            attrs.put("challengeText", "开票触发风控，请完成扫码或短信认证（状态 3999）");
        } else {
            // 6666/7777/4999/5999 及未知：保持挂起
            event.setState(InvoiceState.PROCESSING);
        }
        event.setAttrs(attrs);
        return event;
    }

    /**
     * 回调应答：优先构建加密信封（业务 {@code {"code":"0000"}}），
     * 无同线程配置时退化为业务明文 JSON。
     */
    public static InvoiceCallbackReply reply(boolean success, String message) {
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("code", success ? PiaoTongSpec.REPLY_OK : PiaoTongSpec.REPLY_FAIL);
        if (!success) {
            business.put("msg", StringUtils.isBlank(message) ? "处理失败" : message);
        }
        PiaoTongParams params = REPLY_PARAMS.get();
        REPLY_PARAMS.remove();
        if (params != null && params.hasCredentials()) {
            Map<String, Object> envelope = PiaoTongEnvelope.build(params,
                    JsonUtils.toJson(business));
            return InvoiceCallbackReply.ok(JsonUtils.toJson(envelope)).setSuccess(success)
                    .setMessage(message);
        }
        return InvoiceCallbackReply.ok(JsonUtils.toJson(business)).setSuccess(success)
                .setMessage(message);
    }

    // ==================== 内部 ====================

    private static PiaoTongParams paramsOf(ChannelConfig config) {
        if (config != null && config.getParams() instanceof PiaoTongParams) {
            return (PiaoTongParams) config.getParams();
        }
        throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                "票通回调配置缺失或参数类型不匹配");
    }

    private static String accountKey(InvoiceCallbackRequest raw) {
        if (raw == null || raw.getQueryParams() == null) {
            return null;
        }
        return raw.getQueryParams().get("accountKey");
    }

    private static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text) || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim().substring(0, 10));
        } catch (Exception e) {
            log.debug("票通云回调日期解析失败，按 null 处理，原值: {}", text, e);
            return null;
        }
    }

    static LocalDateTime parseDateTime(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal dec(JsonNode root, String... names) {
        for (String name : names) {
            String value = text(root, name);
            if (StringUtils.isNotBlank(value)) {
                try {
                    return new BigDecimal(value.trim());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个字段
                }
            }
        }
        return null;
    }

    private static String text(JsonNode root, String name) {
        return PiaoTongEnvelope.text(root, name);
    }

    private static void put(Map<String, String> attrs, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            attrs.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String nullTo(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }
}
