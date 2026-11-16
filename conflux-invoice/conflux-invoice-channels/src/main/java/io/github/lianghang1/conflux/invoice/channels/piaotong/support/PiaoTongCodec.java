package io.github.lianghang1.conflux.invoice.channels.piaotong.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongSpec;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 票通业务报文组装与结果归一（文档 2.4~2.34 请求/响应表逐字段核对）。
 *
 * <p>归一约定：</p>
 * <ul>
 *   <li>业务 {@code code}：0000 成功 / 6666 未开 / 7777 开票中 / 9999 失败 /
 *       3999 需认证 / 4999 红单申请中 / 5999 红单审核中；</li>
 *   <li>数电票号码 {@code electronicInvoiceNo} = 发票代码(12) + 发票号码(8)；</li>
 *   <li>金额均为元、两位小数字符串；数量/单价 8 位小数；</li>
 *   <li>零税率/优惠政策/增值税特殊管理映射核心模型国标字段。</li>
 * </ul>
 *
 * @author lianghang
 */
@Slf4j
public final class PiaoTongCodec {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PiaoTongCodec() {
    }

    // ==================== 通用片段 ====================

    /** 20 位发票请求流水号（同一次业务全程复用同一值）。 */
    public static String serialNo(Invoice invoice) {
        Map<String, String> attrs = invoice.getAttrs();
        if (attrs != null && StringUtils.isNotBlank(attrs.get("invoiceReqSerialNo"))) {
            return attrs.get("invoiceReqSerialNo");
        }
        return invoice.getChannelOrderNo() != null
                ? invoice.getChannelOrderNo() : "PT" + System.currentTimeMillis();
    }

    /** 发票种类代码：81 数电专 / 82 数电普 / 08 电子专 / 10 电子普。 */
    public static String invoiceKind(Invoice invoice) {
        switch (invoice.getInvoiceType() == null ? "" : invoice.getInvoiceType().name()) {
            case "DIGITAL_VAT_SPECIAL":
            case "VAT_SPECIAL":
                return PiaoTongSpec.KIND_DIGITAL_SPECIAL;
            case "DIGITAL_VAT_GENERAL":
            case "VAT_GENERAL":
            default:
                return PiaoTongSpec.KIND_DIGITAL_GENERAL;
        }
    }

    /** 数电发票号码：电子发票号码 = 发票代码 + 发票号码（数电时优先单据已存）。 */
    public static String electronicNo(Invoice invoice) {
        if (StringUtils.isNotBlank(invoice.getAttrs() == null
                ? null : invoice.getAttrs().get("electronicInvoiceNo"))) {
            return invoice.getAttrs().get("electronicInvoiceNo");
        }
        if (StringUtils.isNotBlank(invoice.getInvoiceCode())
                && StringUtils.isNotBlank(invoice.getInvoiceNo())) {
            return invoice.getInvoiceCode() + invoice.getInvoiceNo();
        }
        return invoice.getInvoiceNo();
    }

    private static Map<String, Object> base(String taxpayerNum) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("taxpayerNum", taxpayerNum);
        return body;
    }

    // ==================== 2.9 开具蓝字数电发票 ====================

    public static Map<String, Object> issueBody(Invoice invoice, String taxpayerNum,
                                                String serialNo) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("invoiceReqSerialNo", serialNo);
        body.put("invoiceIssueKindCode", invoiceKind(invoice));
        body.put("buyerName", invoice.getBuyerTitle());
        put(body, "buyerTaxpayerNum", invoice.getBuyerTaxNo());
        put(body, "buyerAddress", invoice.getBuyerAddress());
        put(body, "buyerTel", invoice.getBuyerPhone());
        put(body, "buyerBankName", invoice.getBuyerBank());
        put(body, "buyerBankAccount", first(invoice.getAttrs(), "buyerBankAccount"));
        // 收票人：电票交付（票通直接给客户发邮件/短信）
        Map<String, String> attrs = invoice.getAttrs();
        put(body, "takerName", first(attrs, "takerName", "buyerName"));
        put(body, "takerTel", first(attrs, "takerTel", "buyerMobile"));
        put(body, "takerEmail", first(attrs, "takerEmail", "buyerEmail"));
        put(body, "sellerAddress", first(attrs, "sellerAddress"));
        put(body, "sellerTel", first(attrs, "sellerTel"));
        put(body, "sellerBankName", first(attrs, "sellerBankName"));
        put(body, "sellerBankAccount", first(attrs, "sellerBankAccount"));
        put(body, "casherName", first(attrs, "payee", "casherName"));
        put(body, "reviewerName", first(attrs, "checker", "reviewerName"));
        put(body, "remark", invoice.getRemark());
        put(body, "definedData", first(attrs, "definedData"));
        put(body, "tradeNo", invoice.getBizOrderNo());
        body.put("itemList", itemLines(invoice));
        return body;
    }

    /**
     * 明细行（2.9 itemList）。数量/单价 8 位小数、金额/税额 2 位；
     * 免税/优惠映射：{@code zeroTaxFlag}（lslbs）、{@code preferentialPolicyFlag}（yhzcbs）、
     * {@code vatSpecialManage}（zzstsgl）。
     */
    public static List<Map<String, Object>> itemLines(Invoice invoice) {
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        List<InvoiceItem> items = invoice.getItems();
        if (items == null) {
            return lines;
        }
        int index = 1;
        for (InvoiceItem item : items) {
            if (item == null) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<String, Object>();
            line.put("itemNo", String.valueOf(item.getLineNo() == null
                    ? index : item.getLineNo()));
            line.put("itemProperty", "0");
            put(line, "goodsName", item.getItemName());
            put(line, "taxClassificationCode", item.getTaxClassificationCode());
            put(line, "specificationModel", item.getSpecification());
            put(line, "meteringUnit", item.getUnit());
            put(line, "quantity", decimal(item.getQuantity(), 8));
            put(line, "unitPrice", decimal(item.getUnitPrice(), 8));
            put(line, "invoiceAmount", decimal(item.getAmount(), 2));
            put(line, "taxRateValue", decimal(item.getTaxRate(), 2));
            put(line, "taxRateAmount", decimal(item.getTaxAmount(), 2));
            String zeroTax = item.getLslbs();
            if (StringUtils.isNotBlank(zeroTax)) {
                line.put("zeroTaxFlag", zeroTax);
                line.put("preferentialPolicyFlag", "1");
            }
            if (StringUtils.isNotBlank(item.getYhzcbs())) {
                line.put("preferentialPolicyFlag", item.getYhzcbs());
            }
            put(line, "vatSpecialManage", item.getZzstsgl());
            lines.add(line);
            index++;
        }
        return lines;
    }

    // ==================== 2.11 / 2.12 查询 ====================

    public static Map<String, Object> queryBody(String taxpayerNum, String serialNo) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("invoiceReqSerialNo", serialNo);
        return body;
    }

    // ==================== 2.15 文件 ====================

    public static Map<String, Object> fileBody(String taxpayerNum, String serialNo,
                                               String electronicNo, String fileType,
                                               LocalDate invoiceDate) {
        Map<String, Object> body = base(taxpayerNum);
        if (StringUtils.isNotBlank(serialNo)) {
            body.put("invoiceReqSerialNo", serialNo);
        }
        put(body, "electronicInvoiceNo", electronicNo);
        body.put("fileType", fileType);
        if (invoiceDate != null) {
            body.put("invoiceDate", invoiceDate.atStartOfDay().format(DATE_TIME));
        }
        return body;
    }

    // ==================== 2.19 交付重发 ====================

    public static Map<String, Object> resendBody(String taxpayerNum, String serialNo,
                                                 String electronicNo,
                                                 String phone, String email) {
        Map<String, Object> body = base(taxpayerNum);
        if (StringUtils.isNotBlank(serialNo)) {
            body.put("invoiceReqSerialNo", serialNo);
        } else {
            put(body, "electronicInvoiceNo", electronicNo);
        }
        put(body, "takerTel", phone);
        put(body, "takerEmail", email);
        return body;
    }

    // ==================== 2.38 / 2.28 红字确认单申请 ====================

    public static Map<String, Object> redApplyBody(Invoice invoice, RedApplyParams p) {
        Map<String, Object> body = base(p.taxpayerNum);
        body.put("redApplySerialNo", p.redApplySerialNo);
        put(body, "redReason", p.redReason);
        // 数电冲红：blueAllEleInvNo；税控/数电纸票冲红：invoiceCode + invoiceNo
        put(body, "blueAllEleInvNo", p.blueAllEleInvNo);
        put(body, "invoiceCode", p.invoiceCode);
        put(body, "invoiceNo", p.invoiceNo);
        put(body, "blueInvoiceDate", p.blueInvoiceDate);
        body.put("applySource", p.applySource);
        put(body, "invoiceKind", p.invoiceKind);
        put(body, "account", p.account);
        put(body, "buyerTaxpayerNum", invoice == null ? null : invoice.getBuyerTaxNo());
        put(body, "sellerTaxpayerNum", p.taxpayerNum);
        return body;
    }

    /** 2.38 红字申请参数聚合（避免长参数列表）。 */
    public static final class RedApplyParams {
        public String taxpayerNum;
        public String redApplySerialNo;
        public String redReason;
        public String blueAllEleInvNo;
        public String invoiceCode;
        public String invoiceNo;
        public String blueInvoiceDate;
        public String applySource;
        public String invoiceKind;
        public String account;
    }

    // ==================== 2.29 查看索单 / 2.31 审核 / 2.30 红字开具 ====================

    public static Map<String, Object> redViewBody(String taxpayerNum, String redApplySerialNo) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("redApplySerialNo", redApplySerialNo);
        return body;
    }

    public static Map<String, Object> redAuditBody(String taxpayerNum, String redBillNo,
                                                   boolean accept, String account) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("redBillNo", redBillNo);
        body.put("confirmType", accept ? "1" : "0");
        put(body, "account", account);
        return body;
    }

    public static Map<String, Object> redIssueBody(String taxpayerNum, String serialNo,
                                                   Invoice invoice, String redBillNo,
                                                   String account) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("invoiceReqSerialNo", serialNo);
        put(body, "blueInvoiceCode", invoice == null ? null : invoice.getInvoiceCode());
        put(body, "blueInvoiceNo", invoice == null ? null : invoice.getInvoiceNo());
        put(body, "blueAllEleInvNo", invoice == null ? null : electronicNo(invoice));
        body.put("redBillNo", redBillNo);
        put(body, "account", account);
        Map<String, String> attrs = invoice == null ? null : invoice.getAttrs();
        put(body, "takerName", first(attrs, "takerName"));
        put(body, "takerTel", first(attrs, "takerTel", "buyerMobile"));
        put(body, "takerEmail", first(attrs, "takerEmail", "buyerEmail"));
        return body;
    }

    // ==================== 2.33 / 2.34 局端索单 ====================

    public static Map<String, Object> redBureauBody(String taxpayerNum, String businessSerialNo,
                                                    String dateStart, String dateEnd) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("businessSerialNo", businessSerialNo);
        body.put("queryType", "1");
        body.put("applyDateStart", dateStart);
        body.put("applyDateEnd", dateEnd);
        body.put("redBillRange", "1");
        return body;
    }

    public static Map<String, Object> redResultBody(String taxpayerNum, String businessSerialNo) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("businessSerialNo", businessSerialNo);
        return body;
    }

    // ==================== 2.4~2.8 / 2.45 认证链路 ====================

    public static Map<String, Object> accountBody(String taxpayerNum, String account) {
        Map<String, Object> body = base(taxpayerNum);
        put(body, "account", account);
        return body;
    }

    public static Map<String, Object> qrcodeBody(String taxpayerNum, String account,
                                                 String qrcodeType) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("account", account);
        put(body, "qrcodeType", qrcodeType);
        return body;
    }

    public static Map<String, Object> smsLoginBody(String taxpayerNum, String account,
                                                   String smsCode) {
        Map<String, Object> body = base(taxpayerNum);
        body.put("account", account);
        body.put("smsCode", smsCode);
        return body;
    }

    // ==================== 结果归一 ====================

    /**
     * 开票/查单业务结果归一（0000/6666/7777/9999/3999/4999/5999）。
     *
     * <p>0000 且已拿到票号才推进 SUCCEEDED；XML 法定原件由编排层随后调
     * {@code fileFetcher} 补拉（2.15），此处只登记查询响应里带的 PDF 版式。</p>
     */
    public static InvoiceResult issueOrQueryResult(PiaoTongResponse response, String serialNo) {
        String bizCode = response.bizText("code");
        String bizMsg = response.bizText("msg", "resultMsg");
        if (bizCode == null) {
            // 信封失败或无业务报文：失败不推翻在途单据
            if (!response.isAccepted()) {
                return InvoiceResult.failed(nullTo(response.getCode(), "CHANNEL_FAILED"),
                        response.getMessage());
            }
            return InvoiceResult.accepted(serialNo, true);
        }
        if (PiaoTongSpec.ST_SUCCESS.equals(bizCode)
                && StringUtils.isNotBlank(response.bizText("invoiceNo", "electronicInvoiceNo"))) {
            List<InvoiceFile> files = filesFromBusiness(response);
            return InvoiceResult.succeeded(serialNo,
                    response.bizText("invoiceNo"),
                    response.bizText("invoiceCode"),
                    parseDate(response.bizText("invoiceDate")), files)
                    .setAmount(decimalOf(response.bizText("amountWithTax", "noTaxAmount")))
                    .setTaxAmount(decimalOf(response.bizText("taxAmount")));
        }
        if (PiaoTongSpec.ST_FAILED.equals(bizCode)) {
            return InvoiceResult.failed(bizCode, firstNonBlank(bizMsg, "开票失败"));
        }
        // 6666 未开 / 7777 开票中 / 4999 / 5999 红单流程中：保持挂起
        return InvoiceResult.accepted(serialNo, true)
                .setErrorMsg(bizMsg)
                .setRawPayload(response.getRawBody());
    }

    /** 查询/开票响应内嵌的版式文件（invoicePdf 为 Base64 内容）。 */
    public static List<InvoiceFile> filesFromBusiness(PiaoTongResponse response) {
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        String pdf = response.bizText("invoicePdf");
        if (StringUtils.isNotBlank(pdf)) {
            files.add(new InvoiceFile().setType(InvoiceFileType.PDF).setContent(pdf));
        }
        return files;
    }

    /** 2.15 文件响应：resultCode=0000 时返回 base64 内容。 */
    public static String fileContent(PiaoTongResponse response) {
        if (response.getRoot() == null) {
            return null;
        }
        String resultCode = response.bizText("resultCode");
        if (!PiaoTongSpec.ST_SUCCESS.equals(resultCode)) {
            return null;
        }
        return response.bizText("fileContent");
    }

    /** 红字确认单状态归一（processState → RedApplyState 由 Provider 完成，这里透传）。 */
    public static String processState(PiaoTongResponse response) {
        return response.bizText("processState");
    }

    // ==================== 工具 ====================

    public static void put(Map<String, Object> body, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            body.put(key, value);
        }
    }

    public static String first(Map<String, String> attrs, String... names) {
        if (attrs == null) {
            return null;
        }
        for (String name : names) {
            String value = attrs.get(name);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static String nullTo(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    /** 金额/数量转字符串：null 返回 null，固定位数（文档：金额 2 位、数量/单价 8 位）。 */
    public static String decimal(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, BigDecimal.ROUND_HALF_UP).toPlainString();
    }

    public static BigDecimal decimalOf(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            log.warn("票通云金额字段解析失败，按 null 处理，原值: {}", text, e);
            return null;
        }
    }

    public static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim().substring(0, 10));
        } catch (Exception e) {
            log.debug("票通云日期解析失败，按 null 处理，原值: {}", text, e);
            return null;
        }
    }

    public static String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }

    /** 业务报文 JSON（信封 content 的输入）。 */
    public static String toJson(Map<String, Object> body) {
        return JsonUtils.toJson(body);
    }

    /** 红字确认单状态辅助（供测试与 Provider 共用）：是否作废态。 */
    public static boolean isVoidState(String processState) {
        return PiaoTongSpec.PS_VOID_85.equals(processState)
                || PiaoTongSpec.PS_VOID_86.equals(processState)
                || PiaoTongSpec.PS_VOID_87.equals(processState)
                || PiaoTongSpec.PS_VOID_88.equals(processState)
                || PiaoTongSpec.PS_VOID_89.equals(processState)
                || PiaoTongSpec.PS_VOID_90.equals(processState);
    }
}
