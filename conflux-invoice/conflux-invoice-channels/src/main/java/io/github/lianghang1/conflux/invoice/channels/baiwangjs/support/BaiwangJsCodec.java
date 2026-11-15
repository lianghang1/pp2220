package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsParams;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
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

    /**
     * 3.2.7 查单响应归一。<b>查单 status 与回调 status 语义不同</b>（文档 3.2.7）：
     * {@code 2=开具成功、3=开具失败、6=已作废、8=已开未生成PDF、1=开票中}；
     * 回调则是 {@code 1=成功、2=失败}。这里必须用 {@code QRY_*} 常量。
     *
     * <p>仅当明确开具成功且已拿到 XML 法定原件地址时才推进 SUCCEEDED，
     * 其余保持挂起由回调 / 下次轮询推进，绝不把成功票误判为失败。</p>
     */
    public static InvoiceResult queryInvoiceResult(BaiwangJsResponse response, String ddlsh) {
        JsonNode data = dataNode(response);
        String status = text(data, "status");
        List<InvoiceFile> files = layoutFiles(data);
        boolean hasXml = false;
        for (InvoiceFile file : files) {
            if (file.getType() == InvoiceFileType.XML && file.hasPayload()) {
                hasXml = true;
            }
        }
        if (BaiwangJsSpec.QRY_SUCCESS.equals(status) && hasXml) {
            return InvoiceResult.succeeded(ddlsh,
                    text(data, "qdfphm", "fphm"), text(data, "fpdm"), null, files);
        }
        if (BaiwangJsSpec.QRY_FAILED.equals(status)) {
            return InvoiceResult.failed("QUERY_FAILED", text(data, "ycyy", "message"));
        }
        if (BaiwangJsSpec.QRY_DELETED.equals(status)) {
            return InvoiceResult.failed("INVOICE_DELETED",
                    firstNonBlank(text(data, "ycyy", "message"), "发票已作废"));
        }
        // 1开票中 / 8未生成PDF / 0待审核 / 9已审核 / 10待扫码 等：保持挂起
        return InvoiceResult.accepted(ddlsh, true);
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

    /** 3.4.14 认证结果 / 纳税人风险信息查询（异步，回调 cxnsrfx）。 */
    public static Map<String, Object> riskInfoBody(BaiwangJsParams params, String ddlsh) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        return body;
    }

    /** 3.4.5 获取实人认证二维码（异步，回调 ewmrz）。 */
    public static Map<String, Object> qrCodeAsyncBody(BaiwangJsParams params, String ddlsh,
                                                      String ewmlx) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        put(body, "ewmlx", ewmlx == null ? "1" : ewmlx);
        return body;
    }

    // ---- 3.3.12 数电红冲开具 ----
    public static Map<String, Object> redIssueBody(BaiwangJsParams params, String ddlsh,
                                                    String confirmNo) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "dlzh", params.getDlzh());
        put(body, "hzfpxxqrdbh", confirmNo);
        return body;
    }

    // ---- 3.3.15 红字确认单状态查询（同步） ----
    public static Map<String, Object> redStatusBody(BaiwangJsParams params, String ddlsh,
                                                     String confirmNo) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "dlzh", params.getDlzh());
        put(body, "hzfpxxqrdbh", confirmNo);
        return body;
    }

    // ---- 3.3.16 红字确认单确认或拒绝（qrlx=Y/N） ----
    public static Map<String, Object> redConfirmBody(BaiwangJsParams params, String confirmNo,
                                                      boolean accept) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "qyId", params.getQyId());
        put(body, "dlzh", params.getDlzh());
        put(body, "hzfpxxqrdbh", confirmNo);
        put(body, "qrlx", accept ? "Y" : "N");
        return body;
    }

    // ---- 3.3.17 可冲销明细查询（异步，回调 hzcx） ----
    public static Map<String, Object> redAvailableBody(BaiwangJsParams params, String ddlsh,
                                                       Invoice original, String invoiceDate,
                                                       String bizCode, String sourceCode) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "xsfnsrsbh", original == null ? params.getSellerTaxNo()
                : original.getSellerTaxNo());
        put(body, "gmfnsrsbh", original == null ? null : original.getBuyerTaxNo());
        put(body, "dlzh", params.getDlzh());
        put(body, "gjbq", "1");
        put(body, "lzfpdm", original == null ? null : original.getInvoiceCode());
        put(body, "lzfphm", original == null ? null : original.getInvoiceNo());
        put(body, "lzfpqdhm", original == null ? null : original.getInvoiceNo());
        put(body, "lzkprq", invoiceDate);
        put(body, "ywdjDm", bizCode);
        put(body, "fplyDm", sourceCode == null ? "2" : sourceCode);
        return body;
    }

    // ---- 3.4.7 发票短信邮箱重发（同步） ----
    public static Map<String, Object> deliveryBody(BaiwangJsParams params, String invoiceCode,
                                                   String invoiceNo, String phone, String email) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "qyId", params.getQyId());
        put(body, "fpDm", invoiceCode);
        put(body, "fpHm", invoiceNo);
        put(body, "qdfphm", invoiceNo);
        put(body, "phone", phone);
        put(body, "email", email);
        return body;
    }

    // ---- 3.4.8 电子发票下载地址查询（同步，按 ddlsh） ----
    public static Map<String, Object> downloadBody(BaiwangJsParams params, String ddlsh) {
        return baseBody(params, ddlsh);
    }

    // ---- 3.4.15 电票版式文件查询（同步，按票号） ----
    public static Map<String, Object> layoutQueryBody(BaiwangJsParams params, String ddlsh,
                                                      String invoiceCode, String invoiceNo) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "fpdm", invoiceCode);
        put(body, "fphm", invoiceNo);
        put(body, "qdfphm", invoiceNo);
        put(body, "dlzh", params.getDlzh());
        return body;
    }

    // ---- 3.4.11 / 3.4.11(V2) 发票修复（异步） ----
    public static Map<String, Object> repairBody(BaiwangJsParams params, String ddlsh,
                                                 String invoiceTypeCode, String invoiceNo,
                                                 String startDate, String endDate,
                                                 String buyerName) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "nsrsbh", params.getSellerTaxNo());
        put(body, "dlzh", params.getDlzh());
        put(body, "fplxdm", invoiceTypeCode);
        put(body, "sksbh", params.getSellerTaxNo());
        put(body, "cxfs", StringUtils.isNotBlank(startDate) ? "3" : "0");
        put(body, "fphm", invoiceNo);
        put(body, "qsrq", startDate);
        put(body, "zzrq", endDate);
        put(body, "gmfMc", buyerName);
        return body;
    }

    // ---- 3.4.12 发票修复结果查询（同步） ----
    public static Map<String, Object> repairResultBody(BaiwangJsParams params, String ddlsh,
                                                        String elecBz) {
        Map<String, Object> body = baseBody(params, ddlsh);
        put(body, "elecBz", elecBz == null ? "1" : elecBz);
        return body;
    }

    // ---- 3.4.13 发票查询-按票号或时间（同步，批量分页） ----
    public static Map<String, Object> queryBatchBody(BaiwangJsParams params, String cxfs,
                                                     String invoiceTypeCode, String invoiceCode,
                                                     String startNo, String endNo,
                                                     String startDate, String endDate,
                                                     String digitalNo, String pageNo,
                                                     String pageSize) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "qyId", params.getQyId());
        put(body, "fplxdm", invoiceTypeCode);
        put(body, "sksbh", params.getSellerTaxNo());
        put(body, "cxfs", cxfs == null ? "0" : cxfs);
        put(body, "fpdm", invoiceCode);
        put(body, "qshm", startNo);
        put(body, "zzhm", endNo);
        put(body, "qsrq", startDate);
        put(body, "zzrq", endDate);
        put(body, "elecBz", "1");
        put(body, "qdfphm", digitalNo);
        put(body, "pageNo", pageNo == null ? "0" : pageNo);
        put(body, "pageSize", pageSize == null ? "50" : pageSize);
        return body;
    }

    // ==================== 响应解析 ====================

    /** 取 data / Data 节点（兼容平台大小写差异）。 */
    public static JsonNode dataNode(BaiwangJsResponse response) {
        if (response == null || response.getRoot() == null) {
            return null;
        }
        JsonNode root = response.getRoot();
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            data = root.get("Data");
        }
        return data;
    }

    /**
     * 从响应 data 节点提取版式文件地址，兼容三套字段命名：
     * 3.4.15 用 pdfUrl/ofdUrl/xmlUrl，3.4.8 与 3.4.13 用 pdf/ofdurl/xmlurl，
     * qdbswj 回调还可能出现 gsPdf/gsOfdUrl（百旺版式）。
     */
    public static List<InvoiceFile> layoutFiles(JsonNode data) {
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (data == null) {
            return files;
        }
        addFile(files, InvoiceFileType.XML, data, "xmlUrl", "xmlurl", "xml");
        addFile(files, InvoiceFileType.OFD, data, "ofdUrl", "ofdurl", "gsOfdUrl");
        addFile(files, InvoiceFileType.PDF, data, "pdfUrl", "pdf", "gsPdf");
        return files;
    }

    /**
     * 3.4.12 / 3.4.13 修复结果归一（纯函数，无网络依赖）：
     * {@code code=0} 已修复到发票 → SUCCEEDED；
     * {@code code=-1} 失败或尚未修复到发票 → PROCESSING（异步补偿，可继续查）；
     * 其它（如 1003 需要登录）→ FAILED。
     *
     * <p>兼容「字段在 data 节点」与「字段在根节点」两套返回结构，
     * 版式文件地址复用 {@link #layoutFiles(JsonNode)}。</p>
     */
    public static RepairResult repairResult(BaiwangJsResponse response, String ddlsh) {
        String code = response.getCode();
        String msg = response.getReason() == null
                ? response.getMessage() : response.getReason();
        if (BaiwangJsSpec.CODE_OK.equals(code)) {
            JsonNode root = response.getRoot();
            JsonNode data = dataNode(response);
            if (data == null || data.isNull() || !data.isObject()) {
                data = root;
            }
            List<InvoiceFile> files = layoutFiles(data);
            if (files.isEmpty() && root != null && root != data) {
                files = layoutFiles(root);
            }
            String invoiceNo = text(data, "qdfphm", "fphm");
            if (StringUtils.isBlank(invoiceNo) && root != null) {
                invoiceNo = text(root, "qdfphm", "fphm");
            }
            String invoiceCode = text(data, "fpdm");
            if (StringUtils.isBlank(invoiceCode) && root != null) {
                invoiceCode = text(root, "fpdm");
            }
            return RepairResult.succeeded(ddlsh, files)
                    .setInvoiceNo(invoiceNo).setInvoiceCode(invoiceCode)
                    .setRawPayload(response.getRawBody());
        }
        if ("-1".equals(code)) {
            // 文档：code=-1 失败或未修复到发票；修复是异步补偿，保持 PROCESSING 允许继续查
            return RepairResult.accepted(ddlsh)
                    .setErrorMsg(StringUtils.isBlank(msg)
                            ? "尚未修复到发票，可稍后继续查询" : msg)
                    .setRawPayload(response.getRawBody());
        }
        return RepairResult.failed(nullTo(code, "REPAIR_QUERY_FAILED"), msg)
                .setRawPayload(response.getRawBody());
    }

    private static void addFile(List<InvoiceFile> files, InvoiceFileType type, JsonNode data,
                                String... names) {
        String url = text(data, names);
        if (StringUtils.isNotBlank(url)) {
            files.add(new InvoiceFile().setType(type).setUrl(url));
        }
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
