package io.github.lianghang1.conflux.invoice.channels.nuonuo.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoParams;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoSpec;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诺诺报文编解码：组装业务 JSON、解析 SDK 响应、归一状态。
 *
 * <p>字段名与状态码均取自公开文档与官方 SDK 示例（见 {@link NuonuoSpec}），
 * 全部为纯静态方法，便于离线单测。SDK 返回的 {@code result} 在不同接口/版本下
 * 可能是对象、数组或 JSON 字符串，{@link #resultNode} 统一兼容。</p>
 *
 * @author lianghang
 */
public final class NuonuoCodec {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NuonuoCodec() {
    }

    // ==================== 开票报文 ====================

    /**
     * 组装开票业务 JSON（对应 {@code nuonuo.OpeMplatform.requestBillingNew} 的私有参数）。
     * 结构为 {@code {"order": {...}}}。
     */
    public static String issueContent(InvoiceContext ctx, NuonuoParams params,
                                      String orderNo, String callbackUrl) {
        Invoice invoice = ctx.requireInvoice();
        Map<String, Object> order = new LinkedHashMap<String, Object>();

        // 明细
        List<Map<String, Object>> details = new ArrayList<Map<String, Object>>();
        if (invoice.getItems() != null) {
            int idx = 0;
            for (InvoiceItem item : invoice.getItems()) {
                Map<String, Object> detail = new LinkedHashMap<String, Object>();
                put(detail, "goodsName", item.getItemName());
                put(detail, "goodsCode", item.getTaxClassificationCode());
                put(detail, "taxRate", item.getTaxRate());
                put(detail, "tax", item.getTaxAmount());
                put(detail, "taxExcludedAmount", item.getAmount());
                put(detail, "num", item.getQuantity());
                put(detail, "price", item.getUnitPrice());
                put(detail, "unit", item.getUnit());
                // 行性质：0 正常 / 1 折扣 / 2 被折扣；项目明细暂以正常行处理，attrs 可覆盖
                put(detail, "invoiceLineProperty", attr(params, "invoiceLineProperty", "0"));
                // 单价含税标志：0 不含税 / 1 含税
                put(detail, "withTaxFlag", attr(params, "withTaxFlag", "0"));
                put(detail, "deduction", "0");
                details.add(detail);
                idx++;
            }
        }
        order.put("invoiceDetail", details);

        // 购方
        put(order, "buyerName", invoice.getBuyerTitle());
        put(order, "buyerTaxNum", invoice.getBuyerTaxNo());
        put(order, "buyerPhone", firstNotBlank(invoice.getBuyerMobile(), invoice.getBuyerPhone()));
        put(order, "buyerAddress", invoice.getBuyerAddress());
        put(order, "email", invoice.getBuyerEmail());

        // 销方 / 开票要素
        put(order, "salerTaxNum", firstNotBlank(params.getSellerTaxNo(), invoice.getSellerTaxNo()));
        put(order, "clerk", firstNotBlank(params.getDrawer(), invoice.getDrawer()));
        put(order, "orderNo", orderNo);
        put(order, "invoiceType", attr(params, "invoiceType", "1"));
        put(order, "invoiceLine", attr(params, "invoiceLine", "p"));
        put(order, "listFlag", "0");
        String pushMode = attr(params, "pushMode", null);
        if (pushMode != null) {
            order.put("pushMode", pushMode);
        }
        put(order, "callBackUrl", callbackUrl);
        order.put("invoiceDate", LocalDateTime.now().format(DATE_TIME));

        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("order", order);
        return JsonUtils.toJson(root);
    }

    /** 查单业务 JSON：按发票流水号查询。 */
    public static String queryContent(String serialNo, String orderNo) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        List<String> serials = new ArrayList<String>();
        if (StringUtils.isNotBlank(serialNo)) {
            serials.add(serialNo);
        }
        root.put("serialNos", serials);
        if (StringUtils.isNotBlank(orderNo)) {
            List<String> orders = new ArrayList<String>();
            orders.add(orderNo);
            root.put("orderNos", orders);
        }
        root.put("isOfferInvoiceDetail", "1");
        return JsonUtils.toJson(root);
    }

    // ==================== 响应解析 ====================

    /** 开票受理：code=E0000 且 result.invoiceSerialNum。 */
    public static InvoiceResult issueResult(String responseJson, String orderNo) {
        JsonNode root = parse(responseJson);
        if (!successCode(root, NuonuoSpec.CODE_BILLING_OK, NuonuoSpec.CODE_OK)) {
            return InvoiceResult.failed(code(root), describe(root));
        }
        JsonNode result = resultNode(root);
        String serialNo = text(result, "invoiceSerialNum", "serialNo");
        if (StringUtils.isBlank(serialNo)) {
            return InvoiceResult.failed("NO_SERIAL", "诺诺开票受理未返回发票流水号");
        }
        return InvoiceResult.accepted(serialNo, true)
                .setBizOrderNo(orderNo)
                .setRawPayload(responseJson);
    }

    /** 查单：result 数组取第一条，按 status 归一。 */
    public static InvoiceResult queryResult(String responseJson, String serialNo, String orderNo) {
        JsonNode root = parse(responseJson);
        if (!successCode(root, NuonuoSpec.CODE_OK, NuonuoSpec.CODE_BILLING_OK)) {
            return InvoiceResult.failed(code(root), describe(root));
        }
        JsonNode result = resultNode(root);
        JsonNode first = result == null ? null : (result.isArray() ? result.get(0) : result);
        if (first == null || first.isNull()) {
            // 查询无数据：保持受理中，不推翻在途单据
            return InvoiceResult.accepted(serialNo, true);
        }
        String status = text(first, "status");
        String channelOrderNo = firstNotBlank(text(first, "serialNo"), serialNo);
        InvoiceState state = stateOfQuery(status);
        if (state == InvoiceState.SUCCEEDED) {
            return InvoiceResult.succeeded(
                    channelOrderNo,
                    text(first, "invoiceNo"),
                    text(first, "invoiceCode"),
                    parseDate(text(first, "invoiceTime")),
                    null)
                    .setBizOrderNo(firstNotBlank(text(first, "orderNo"), orderNo))
                    .setRawPayload(responseJson);
        }
        if (state == InvoiceState.FAILED) {
            return InvoiceResult.failed(nullTo(text(first, "statusMsg"), "INVOICE_FAILED"),
                    text(first, "statusMsg"));
        }
        return InvoiceResult.accepted(channelOrderNo, true)
                .setBizOrderNo(firstNotBlank(text(first, "orderNo"), orderNo))
                .setRawPayload(responseJson);
    }

    /** 归一查单状态。 */
    public static InvoiceState stateOfQuery(String status) {
        if (NuonuoSpec.QRY_SUCCESS.equals(status)) {
            return InvoiceState.SUCCEEDED;
        }
        if (NuonuoSpec.QRY_FAILED.equals(status)) {
            return InvoiceState.FAILED;
        }
        // 20 开票中 / 21 签章中 / 其他未知：保持处理中
        return InvoiceState.PROCESSING;
    }

    /** 归一回调通知状态（与查单 status 语义不同，严禁混用）。 */
    public static InvoiceState stateOfNotice(String status) {
        if (NuonuoSpec.NOTICE_SUCCESS.equals(status)) {
            return InvoiceState.SUCCEEDED;
        }
        if (NuonuoSpec.NOTICE_FAILED.equals(status)
                || NuonuoSpec.NOTICE_SIGN_FAILED.equals(status)) {
            return InvoiceState.FAILED;
        }
        return InvoiceState.PROCESSING;
    }

    // ==================== 工具 ====================

    /** SDK 的 result 可能是对象、数组或 JSON 字符串，统一解析为 JsonNode。 */
    public static JsonNode resultNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            return null;
        }
        if (result.isTextual()) {
            try {
                return JsonUtils.readTree(result.asText());
            } catch (RuntimeException e) {
                return null;
            }
        }
        return result;
    }

    private static JsonNode parse(String json) {
        if (StringUtils.isBlank(json)) {
            throw new io.github.lianghang1.conflux.common.exception.GatewayException(
                    io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "诺诺响应为空");
        }
        try {
            return JsonUtils.readTree(json);
        } catch (RuntimeException e) {
            throw new io.github.lianghang1.conflux.common.exception.GatewayException(
                    io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "诺诺响应不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private static boolean successCode(JsonNode root, String... codes) {
        String code = code(root);
        for (String c : codes) {
            if (c.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String code(JsonNode root) {
        return root == null ? null : text(root, "code");
    }

    private static String describe(JsonNode root) {
        return root == null ? null : firstNotBlank(text(root, "describe"), text(root, "message"));
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
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

    private static String attr(NuonuoParams params, String key, String fallback) {
        if (params == null || params.getAttrs() == null) {
            return fallback;
        }
        String v = params.getAttrs().get(key);
        return StringUtils.isBlank(v) ? fallback : v;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String firstNotBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String nullTo(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }
}
