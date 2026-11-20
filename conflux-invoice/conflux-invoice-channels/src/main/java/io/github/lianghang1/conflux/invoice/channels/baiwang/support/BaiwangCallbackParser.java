package io.github.lianghang1.conflux.invoice.channels.baiwang.support;

import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百望云销项回调解析（文档《百望云-销项回调接口文档1.0.6》）。
 *
 * <p>回调为百望主动 POST：外层 {@code {method,type,taxNo,version,data,sign}}，
 * 其中 {@code data} 是业务 JSON 字符串。{@code method} 区分业务类型
 * （{@code baiwang.s.callback.invoice} 开票回调等），{@code type} 区分
 * 01-开票成功/02-开票失败/05-删除。文件地址（xmlUrl/pdfUrl/ofdUrl）在 data 内。</p>
 *
 * <p><b>验签</b>：宿主配置 {@code attr.callbackSignKey} 时，对
 * {@code data + "&" + type} 做 HMAC-SHA256 校验；未配置时不校验。</p>
 *
 * @author lianghang
 */
@Slf4j
public final class BaiwangCallbackParser {

    private BaiwangCallbackParser() {
    }

    public static InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        if (raw == null || StringUtils.isBlank(raw.getBody())) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "百望云回调报文为空");
        }
        Map<String, Object> root;
        try {
            root = JsonUtils.fromJson(raw.getBody(), Map.class);
        } catch (RuntimeException e) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百望云回调报文不是合法 JSON: " + e.getMessage(), e);
        }
        String method = str(root, "method");
        String type = str(root, "type");

        Object rawData = root.get("data");
        String dataText = rawData instanceof String ? (String) rawData : (rawData == null ? "" : String.valueOf(rawData));
        Map<String, Object> data = parseData(rawData);
        assertSign(config, dataText, type, str(root, "sign"));

        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setRawPayload(raw.getBody());

        if (BaiwangSpec.METHOD_CALLBACK_INVOICE.equals(method)) {
            fillInvoice(event, data, type);
        } else if (BaiwangSpec.METHOD_CALLBACK_RED_CONFIRM.equals(method)
                || BaiwangSpec.METHOD_CALLBACK_RED_INFO.equals(method)) {
            fillRed(event, data, method);
        } else if (BaiwangSpec.METHOD_CALLBACK_SCAN.equals(method)) {
            // 扫码开票提交结果：只登记流水，不迁移发票状态
            event.setChannelOrderNo(firstNonBlank(str(data, "serialNo"), str(data, "orderNo")))
                    .setBizOrderNo(str(data, "orderNo"))
                    .setAttr(attrOf(method, type, str(root, "taxNo")));
        } else {
            // 未知业务类型：只登记，不迁移状态
            event.setAttr(attrOf(method, type, str(root, "taxNo")));
        }
        return event;
    }

    /** 开票回调：按 type 归一状态，data 内联的文件地址直接归档。 */
    private static void fillInvoice(InvoiceCallbackEvent event, Map<String, Object> data, String type) {
        event.setChannelOrderNo(firstNonBlank(str(data, "serialNo"), str(data, "orderNo")))
                .setBizOrderNo(str(data, "orderNo"))
                .setInvoiceNo(str(data, "invoiceNo"))
                .setInvoiceCode(str(data, "invoiceCode"))
                .setInvoiceDate(parseDate(str(data, "invoiceDate")))
                .setAmount(decimal(data.get("invoiceTotalPriceTax")))
                .setTaxAmount(decimal(data.get("invoiceTotalTax")))
                .setState(BaiwangCodec.stateOfCallbackType(type));
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        addFile(files, InvoiceFileType.XML, str(data, "xmlUrl"));
        addFile(files, InvoiceFileType.PDF, str(data, "pdfUrl"));
        addFile(files, InvoiceFileType.OFD, str(data, "ofdUrl"));
        if (!files.isEmpty()) {
            event.setFiles(files);
        }
    }

    /** 红冲确认/红字信息回调：只登记确认状态，不推进蓝票。 */
    private static void fillRed(InvoiceCallbackEvent event, Map<String, Object> data, String method) {
        event.setRedConfirmNo(firstNonBlank(str(data, "redInfoNo"), str(data, "redConfirmNo")))
                .setChannelOrderNo(str(data, "serialNo"))
                .setAttr(attrOf(method, null, str(data, "sellerTaxNo")));
        String status = firstNonBlank(str(data, "redConfirmStatus"), str(data, "redInfoStatus"),
                str(data, "openStatus"));
        if (BaiwangSpec.RED_CONFIRMED.equals(status)) {
            event.setRedState(io.github.lianghang1.conflux.invoice.model.RedApplyState.CONFIRMED);
        } else if (BaiwangSpec.RED_REJECTED.equals(status)) {
            event.setRedState(io.github.lianghang1.conflux.invoice.model.RedApplyState.REJECTED);
        }
    }

    /** 可选验签：宿主配置 callbackSignKey 时校验 HMAC(data原文 + "&" + type)。 */
    private static void assertSign(ChannelConfig config, String dataText, String type, String sign) {
        if (config == null || config.getAttr() == null || StringUtils.isBlank(sign)) {
            return;
        }
        String key = config.getAttr().get("callbackSignKey");
        if (StringUtils.isBlank(key)) {
            return;
        }
        String payload = dataText + "&" + (type == null ? "" : type);
        String expected = SignUtil.hmacSha256Hex(payload, key);
        if (!SignUtil.constantTimeEquals(expected, sign)) {
            throw new GatewayException(InvoiceErrorCode.SIGN_INVALID, "百望云回调验签失败");
        }
    }

    /** 应答：与百旺金穗云一致的成功/失败 JSON。 */
    public static InvoiceCallbackReply reply(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", success ? "0" : "1");
        body.put("message", success ? "成功" : (message == null ? "失败" : message));
        return InvoiceCallbackReply.ok(JsonUtils.toJson(body));
    }

    // ==================== 工具 ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseData(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        try {
            Map<String, Object> parsed = JsonUtils.fromJson(String.valueOf(raw), Map.class);
            return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
        } catch (RuntimeException e) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百望云回调 data 不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> attrOf(String method, String type, String taxNo) {
        Map<String, String> attr = new LinkedHashMap<String, String>();
        put(attr, "method", method);
        put(attr, "type", type);
        put(attr, "taxNo", taxNo);
        return attr;
    }

    private static void put(Map<String, String> attr, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            attr.put(key, value);
        }
    }

    private static void addFile(List<InvoiceFile> files, InvoiceFileType type, String url) {
        if (StringUtils.isNotBlank(url)) {
            files.add(new InvoiceFile().setType(type).setUrl(url));
        }
    }

    @SuppressWarnings("unchecked")
    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
}
