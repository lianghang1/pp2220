package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsParams;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApply;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百旺金穗云报文编解码（纯函数，无网络依赖）。
 *
 * <p><b>传输约定</b>（语雀《1.接口概述》）：请求为 JSON 经 Base64(UTF-8) 转码后的字符串，
 * Content-Type 仍为 {@code application/json}；同步响应为明文 JSON；异步回调为
 * {@code text/plain} 且 body 是 Base64 明文。</p>
 *
 * <p><b>版本差异</b>：V1 用合并字段 {@code xsfDzdh/xsfYhzh/gmfDzdh/gmfYhzh}
 * （地址与电话、银行与账号以 {@code ~~} 分隔），明细带 {@code xh}；
 * V2 用拆分字段 {@code xsfDz/xsfDh/xsfYh/xsfZh/gmfDz/gmfDh/gmfYh/gmfZh}。</p>
 *
 * @author lianghang
 */
public final class BaiwangJsCodec {

    private BaiwangJsCodec() {
    }

    // ==================== Base64 ====================

    /** JSON 请求对象 → Base64(UTF-8) 报文体。 */
    public static String encodeRequest(Map<String, Object> body) {
        String json = JsonUtils.toJson(body);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 明文 → JSON 字符串；非法 Base64 抛 CALLBACK_PARSE_FAILED。 */
    public static String decodeBody(String base64) {
        if (StringUtils.isBlank(base64)) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百旺金穗云报文为空");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64.trim());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百旺金穗云报文不是合法 Base64: " + e.getMessage());
        }
    }

    /** 宽容解析：优先按 Base64 解码，失败时若本身是合法 JSON 则直接返回（兼容明文回调）。 */
    public static String decodeOrPlain(String body) {
        if (StringUtils.isBlank(body)) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百旺金穗云报文为空");
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        return decodeBody(trimmed);
    }

    // ==================== 同步响应 ====================

    /** 解析同步响应；非 JSON 抛 CHANNEL_CALL_FAILED。 */
    public static BaiwangJsResponse parseResponse(String body) {
        JsonNode root;
        try {
            root = JsonUtils.readTree(body);
        } catch (IllegalArgumentException e) {
            // JsonUtils 统一抛 JDK 异常，渠道层需包装为框架异常，保留 cause
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "百旺金穗云返回非 JSON 报文: " + abbreviate(body), e);
        }
        if (root == null || !root.isObject()) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "百旺金穗云返回非 JSON 报文: {}", abbreviate(body));
        }
        return new BaiwangJsResponse()
                .setCode(text(root, "code"))
                .setMessage(text(root, "message", "messages"))
                .setReason(text(root, "renson", "reason"))
                .setRawBody(body)
                .setRoot(root);
    }

    /** 同步受理结果 → 框架四态之一：受理成功即 PROCESSING（真实结果走回调）。 */
    public static InvoiceResult acceptedOrFailed(BaiwangJsResponse response, String ddlsh) {
        if (response == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "百旺金穗云未返回同步响应");
        }
        if (BaiwangJsSpec.CODE_OK.equals(response.getCode())) {
            return InvoiceResult.accepted(ddlsh, true);
        }
        return InvoiceResult.failed(nullTo(response.getCode(), "CHANNEL_ERROR"),
                defaultMsg(response));
    }

    private static String defaultMsg(BaiwangJsResponse response) {
        String reason = response.getReason();
        return StringUtils.isNotBlank(reason) ? reason
                : (StringUtils.isNotBlank(response.getMessage())
                    ? response.getMessage() : "渠道同步受理失败");
    }

    // ==================== 请求组装：公共 ====================

    /** 平台级必填公共字段。 */
    public static Map<String, Object> baseBody(BaiwangJsParams params, String ddlsh) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "qyId", params.getQyId());
        put(body, "ddlsh", ddlsh);
        return body;
    }

    /** 3.2.6 / 3.2.6(V2) 蓝字开票请求体。 */
    public static Map<String, Object> issueBody(InvoiceContext ctx, BaiwangJsParams params,
                                                String ddlsh) {
        Invoice invoice = ctx.requireInvoice();
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "sksbh", invoice.getSellerTaxNo());
        put(body, "fplxdm", params.invoiceTypeCode(invoice.getInvoiceType()));
        put(body, "kplx", "0");
        put(body, "tspz", "00");
        put(body, "zsfs", "0");
        put(body, "qdbz", "0");

        String sellerName = firstNonBlank(params.getSellerName(), invoice.getSellerName());
        put(body, "xsfMc", sellerName);
        put(body, "xsfNsrsbh", invoice.getSellerTaxNo());
        fillParty(body, params, invoice, params.isV1());

        put(body, "gmfMc", invoice.getBuyerTitle());
        put(body, "gmfNsrsbh", invoice.getBuyerTaxNo());
        put(body, "kpr", firstNonBlank(params.getKpr(), invoice.getDrawer()));
        put(body, "sqr", params.getSqr());
        put(body, "dlzh", params.getDlzh());
        put(body, "bz", invoice.getRemark());

        BigDecimal total = invoice.getAmount();
        BigDecimal tax = invoice.getTaxAmount();
        put(body, "jshj", decimal(total));
        put(body, "hjse", decimal(tax));
        if (total != null) {
            put(body, "hjje", decimal(tax == null ? total : total.subtract(tax)));
        }
        body.put("detailParam", detailParams(invoice, params.isV1(), false));
        return body;
    }

    /** V1 合并 / V2 拆分 的购销方地址电话银行账号。 */
    private static void fillParty(Map<String, Object> body, BaiwangJsParams params,
                                  Invoice invoice, boolean v1) {
        if (v1) {
            put(body, "xsfDzdh", joinTilde(params.getSellerAddress(), params.getSellerPhone()));
            put(body, "xsfYhzh", joinTilde(params.getSellerBank(), params.getSellerBankAccount()));
            put(body, "gmfDzdh", joinTilde(invoice.getBuyerAddress(), invoice.getBuyerPhone()));
            put(body, "gmfYhzh", joinTilde(invoice.getBuyerBank(), null));
        } else {
            put(body, "xsfDz", params.getSellerAddress());
            put(body, "xsfDh", params.getSellerPhone());
            put(body, "xsfYh", params.getSellerBank());
            put(body, "xsfZh", params.getSellerBankAccount());
            put(body, "gmfDz", invoice.getBuyerAddress());
            put(body, "gmfDh", invoice.getBuyerPhone());
            put(body, "gmfYh", invoice.getBuyerBank());
            put(body, "gmfZh", null);
        }
    }

    /** 明细行；{@code negative=true} 用于红字申请（金额为负）。 */
    private static List<Map<String, Object>> detailParams(Invoice invoice, boolean v1,
                                                          boolean negative) {
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        if (invoice.getItems() == null) {
            return lines;
        }
        int index = 1;
        for (InvoiceItem item : invoice.getItems()) {
            if (item == null) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            if (v1) {
                line.put("xh", item.getLineNo() == null ? index : item.getLineNo());
            }
            put(line, "fphxz", "0");
            put(line, "hsbz", "1");
            put(line, "spmc", item.getItemName());
            put(line, "spbm", item.getTaxClassificationCode());
            put(line, "ggxh", item.getSpecification());
            put(line, "dw", item.getUnit());
            put(line, "dj", decimal(item.getUnitPrice()));
            put(line, "sl", decimal(item.getQuantity()));
            put(line, "je", decimal(negative ? negate(item.getAmount()) : item.getAmount()));
            put(line, "se", decimal(negative ? negate(item.getTaxAmount()) : item.getTaxAmount()));
            put(line, "slv", decimal(item.getTaxRate()));
            put(line, "lslbs", "0");
            put(line, "yhzcbs", "0");
            lines.add(line);
            index++;
        }
        return lines;
    }

    /** 3.2.7 / 3.2.7(V2) 按订单流水号查单。 */
    public static Map<String, Object> queryBody(BaiwangJsParams params, String ddlsh) {
        return baseBody(params, ddlsh);
    }

    /** 3.1.1 查询企业唯一标识。 */
    public static Map<String, Object> uniqueSignBody(String sellerName, String sellerTaxNo) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "nsrmc", sellerName);
        put(body, "nsrsbh", sellerTaxNo);
        return body;
    }

    /** 3.3.3 数电红冲申请（两段红冲第一段）。 */
    public static Map<String, Object> redApplyBody(InvoiceContext ctx, BaiwangJsParams params,
                                                   String ddlsh) {
        Invoice invoice = ctx.requireInvoice();
        RedApply apply = ctx.getRedApply();
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "dlzh", params.getDlzh());
        put(body, "sksbh", invoice.getSellerTaxNo());
        put(body, "fplxdm", params.invoiceTypeCode(invoice.getInvoiceType()));
        put(body, "kplx", "1");
        put(body, "tspz", "00");
        put(body, "zsfs", "0");
        put(body, "qdbz", "0");
        put(body, "xsfMc", firstNonBlank(params.getSellerName(), invoice.getSellerName()));
        put(body, "xsfNsrsbh", invoice.getSellerTaxNo());
        fillParty(body, params, invoice, params.isV1());
        put(body, "gmfMc", invoice.getBuyerTitle());
        put(body, "gmfNsrsbh", invoice.getBuyerTaxNo());
        put(body, "sqr", params.getSqr());
        put(body, "bz", apply == null ? null : apply.getReason());

        BigDecimal amount = apply == null || apply.getAmount() == null
                ? invoice.getAmount() : apply.getAmount();
        BigDecimal tax = invoice.getTaxAmount();
        put(body, "jshj", decimal(negate(amount)));
        put(body, "hjse", decimal(negate(tax)));
        if (amount != null) {
            put(body, "hjje", decimal(negate(tax == null ? amount : amount.subtract(tax))));
        }
        put(body, "yfpDm", apply == null ? invoice.getInvoiceCode()
                : apply.getOriginalInvoiceCode());
        put(body, "yfpHm", apply == null ? invoice.getInvoiceNo()
                : apply.getOriginalInvoiceNo());
        put(body, "chyydm", "1");
        body.put("detailParam", detailParams(invoice, params.isV1(), true));
        return body;
    }

    /** 3.3.4 数电红冲结果查询。 */
    public static Map<String, Object> redQueryBody(BaiwangJsParams params, String ddlsh) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "dlzh", params.getDlzh());
        return body;
    }

    /** 3.4.2 电子税务局登录；sms 为空表示第一步（请求发送验证码）。 */
    public static Map<String, Object> loginBody(BaiwangJsParams params, String sms) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "qyId", params.getQyId());
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        put(body, "sms", sms);
        return body;
    }

    /** 3.4.4 获取实人认证二维码（同步）。 */
    public static Map<String, Object> qrCodeBody(BaiwangJsParams params, String ddlsh) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        put(body, "ewmlx", "1");
        return body;
    }

    /** 3.4.5 认证结果查询。 */
    public static Map<String, Object> riskInfoBody(BaiwangJsParams params, String ddlsh) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        return body;
    }

    // ==================== 工具 ====================

    /** 订单流水号：直接使用业务单号，保证全局唯一、失败重试可用原值、回调可按 bizOrderNo 反查。 */
    public static String ddlsh(Invoice invoice) {
        return invoice.getBizOrderNo();
    }

    public static String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && text.length() > 0) {
                    return text;
                }
            }
        }
        return null;
    }

    public static BigDecimal decimalValue(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).length() == 0) {
            return;
        }
        body.put(key, value);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal negate(BigDecimal value) {
        return value == null ? null : value.negate();
    }

    private static String joinTilde(String left, String right) {
        if (StringUtils.isBlank(left) && StringUtils.isBlank(right)) {
            return null;
        }
        return (left == null ? "" : left) + "~~" + (right == null ? "" : right);
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

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }
}
