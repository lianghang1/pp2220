package io.github.lianghang1.conflux.invoice.channels.baiwang.support;

import com.baiwang.open.entity.request.SOutputinvoiceFastRedRequest;
import com.baiwang.open.entity.request.SOutputinvoiceInvoiceRequest;
import com.baiwang.open.entity.request.SOutputinvoicePushRequest;
import com.baiwang.open.entity.request.SOutputinvoiceQueryRequest;
import com.baiwang.open.entity.request.SOutputinvoiceRetryRequest;
import com.baiwang.open.entity.request.SRedconfirmOperateRequest;
import com.baiwang.open.entity.request.SRedconfirmQueryRequest;
import com.baiwang.open.entity.request.node.SOutputinvoiceFastRedDetail;
import com.baiwang.open.entity.request.node.SOutputinvoiceInvoiceInvoiceDetail;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangParams;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangSpec;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 百望云报文编解码与结果归一。
 *
 * <p>请求组装为 {@code SOutputinvoice*}/{@code SRedconfirm*} 强类型对象；
 * 响应转成 Map 后按「响应业务参数」字段名读取。</p>
 *
 * <p>开票接口异步受理，同步响应只有申请流水号 serialNo，结果置 PROCESSING；
 * 查单返回票号且状态完成时才置 SUCCEEDED。</p>
 *
 * @author lianghang
 */
public final class BaiwangCodec {

    private BaiwangCodec() {
    }

    // ==================== 开票请求 ====================

    /** 组装开票请求；orderNo（开票单号）取自使用方单据。 */
    public static SOutputinvoiceInvoiceRequest issueRequest(InvoiceContext ctx, BaiwangParams params) {
        Invoice invoice = ctx.requireInvoice();
        SOutputinvoiceInvoiceRequest request = new SOutputinvoiceInvoiceRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setInvoiceTerminalCode(params.getInvoiceTerminalCode());
        request.setOrderNo(invoice.getBizOrderNo());
        request.setBizOrderNo(invoice.getBizOrderNo());
        request.setOrderDateTime(BaiwangSpec.nowText());
        request.setInvoiceType(BaiwangSpec.INVOICE_TYPE_BLUE);
        request.setInvoiceTypeCode(invoiceTypeCode(params, invoice.getInvoiceType()));
        request.setBuyerName(invoice.getBuyerTitle());
        request.setBuyerTaxNo(invoice.getBuyerTaxNo());
        request.setBuyerAddress(invoice.getBuyerAddress());
        request.setBuyerTelephone(invoice.getBuyerPhone());
        request.setBuyerBankName(invoice.getBuyerBank());
        request.setPushEmail(invoice.getBuyerEmail());
        request.setPushPhone(invoice.getBuyerMobile());
        request.setSellerAddress(params.getSellerAddress());
        request.setSellerPhone(params.getSellerPhone());
        request.setSellerBankName(params.getSellerBankName());
        request.setSellerBankNumber(params.getSellerBankNumber());
        request.setDrawer(firstNonBlank(invoice.getDrawer(), params.getDrawer()));
        request.setPayee(params.getPayee());
        request.setChecker(params.getChecker());
        request.setRemarks(invoice.getRemark());
        request.setCallBackUrl(params.getCallbackUrl());
        request.setPriceTaxMark(BaiwangSpec.PRICE_TAX_EXCLUSIVE);
        request.setInvoiceDetailList(issueDetails(invoice));
        return request;
    }

    /** 明细行映射：框架 InvoiceItem → 文档 SOutputinvoiceInvoiceInvoiceDetail。 */
    private static List<SOutputinvoiceInvoiceInvoiceDetail> issueDetails(Invoice invoice) {
        List<InvoiceItem> items = invoice.getItems();
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<SOutputinvoiceInvoiceInvoiceDetail> details = new ArrayList<SOutputinvoiceInvoiceInvoiceDetail>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            SOutputinvoiceInvoiceInvoiceDetail d = new SOutputinvoiceInvoiceInvoiceDetail();
            d.setGoodsLineNo(item.getLineNo() == null ? String.valueOf(i + 1) : String.valueOf(item.getLineNo()));
            d.setGoodsName(item.getItemName());
            d.setGoodsSpecification(item.getSpecification());
            d.setGoodsUnit(item.getUnit());
            d.setGoodsQuantity(plain(item.getQuantity()));
            d.setGoodsPrice(plain(item.getUnitPrice()));
            d.setGoodsTotalPrice(plain(item.getAmount()));
            d.setGoodsTotalTax(plain(item.getTaxAmount()));
            d.setGoodsTaxRate(plain(item.getTaxRate()));
            d.setGoodsCode(item.getTaxClassificationCode());
            d.setFreeTaxMark(item.getLslbs());
            d.setPreferentialMark(item.getYhzcbs());
            d.setVatSpecialManagement(item.getZzstsgl());
            details.add(d);
        }
        return details;
    }

    // ==================== 查询请求 ====================

    /**
     * 组装查询请求。文档按 serialNos/orderNos 列表查询，二者取其一；
     * 优先用使用方开票单号 orderNo，没有则用渠道流水号。
     */
    public static SOutputinvoiceQueryRequest queryRequest(BaiwangParams params,
                                                          String orderNo, String serialNo) {
        SOutputinvoiceQueryRequest request = new SOutputinvoiceQueryRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setDetailMark(BaiwangSpec.DETAIL_MARK_YES);
        if (StringUtils.isNotBlank(orderNo)) {
            request.setOrderNos(Collections.singletonList(orderNo));
        } else if (StringUtils.isNotBlank(serialNo)) {
            request.setSerialNos(Collections.singletonList(serialNo));
        }
        return request;
    }

    // ==================== 红冲 / 交付 / 重推 请求 ====================

    /** 快捷冲红：fastIssueRedType=0 只生成确认单，=1 自动生成红票。 */
    public static SOutputinvoiceFastRedRequest fastRedRequest(BaiwangParams params, Invoice invoice,
                                                              RedApply redApply, String fastIssueRedType) {
        SOutputinvoiceFastRedRequest request = new SOutputinvoiceFastRedRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setInvoiceTerminalCode(params.getInvoiceTerminalCode());
        request.setOrderNo(invoice == null ? null : invoice.getBizOrderNo());
        request.setOriginalOrderNo(invoice == null ? null : invoice.getBizOrderNo());
        request.setOriginalInvoiceCode(invoice == null ? null : invoice.getInvoiceCode());
        request.setOriginalInvoiceNo(invoice == null ? null : invoice.getInvoiceNo());
        request.setFastIssueRedType(fastIssueRedType);
        request.setCallBackUrl(params.getCallbackUrl());
        if (redApply != null) {
            request.setRedInvoiceLabel(firstNonBlank(redApply.getChyydm(), redApply.getReason()));
        }
        List<SOutputinvoiceFastRedDetail> details = fastRedDetails(invoice);
        if (!details.isEmpty()) {
            request.setDetails(details);
        }
        return request;
    }

    private static List<SOutputinvoiceFastRedDetail> fastRedDetails(Invoice invoice) {
        if (invoice == null || invoice.getItems() == null || invoice.getItems().isEmpty()) {
            return Collections.emptyList();
        }
        List<SOutputinvoiceFastRedDetail> details = new ArrayList<SOutputinvoiceFastRedDetail>();
        for (InvoiceItem item : invoice.getItems()) {
            SOutputinvoiceFastRedDetail d = new SOutputinvoiceFastRedDetail();
            d.setGoodsName(item.getItemName());
            d.setGoodsCode(item.getTaxClassificationCode());
            d.setGoodsQuantity(plain(item.getQuantity()));
            d.setGoodsPrice(plain(item.getUnitPrice()));
            d.setGoodsTaxRate(plain(item.getTaxRate()));
            d.setGoodsTotalPrice(plain(item.getAmount()));
            d.setGoodsTotalTax(plain(item.getTaxAmount()));
            details.add(d);
        }
        return details;
    }

    /** 红字确认单操作：01 确认 / 02 否认 / 03 撤销。 */
    public static SRedconfirmOperateRequest redConfirmOperateRequest(BaiwangParams params,
                                                                      String confirmNo, String confirmType) {
        SRedconfirmOperateRequest request = new SRedconfirmOperateRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setSellerTaxNo(params.getSellerTaxNo());
        request.setRedConfirmNo(confirmNo);
        request.setConfirmType(confirmType);
        request.setCallBackUrl(params.getCallbackUrl());
        return request;
    }

    /** 红字确认单查询（库里查询）。 */
    public static SRedconfirmQueryRequest redConfirmQueryRequest(BaiwangParams params, String confirmNo) {
        SRedconfirmQueryRequest request = new SRedconfirmQueryRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setOperatorType(BaiwangSpec.RED_CONFIRM_OP_QUERY);
        request.setBuySelSelector(BaiwangSpec.SELECTOR_SELLER);
        if (StringUtils.isNotBlank(confirmNo)) {
            request.setRedConfirmNo(confirmNo);
        }
        return request;
    }

    /** 交付：票号 + 手机/邮箱。 */
    public static SOutputinvoicePushRequest pushRequest(BaiwangParams params, Invoice invoice,
                                                         String phone, String email) {
        SOutputinvoicePushRequest request = new SOutputinvoicePushRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setInvoiceCode(invoice.getInvoiceCode());
        request.setInvoiceNo(invoice.getInvoiceNo());
        request.setPushPhone(phone);
        request.setPushEmail(email);
        return request;
    }

    /** 失败重推。 */
    public static SOutputinvoiceRetryRequest retryRequest(BaiwangParams params, String orderNo) {
        SOutputinvoiceRetryRequest request = new SOutputinvoiceRetryRequest();
        request.setTaxNo(params.getSellerTaxNo());
        request.setOrderNo(orderNo);
        request.setInvoiceTerminalCode(params.getInvoiceTerminalCode());
        return request;
    }

    // ==================== 响应归一 ====================

    /** 开票受理：同步只返回 serialNo，一律 PROCESSING。 */
    public static InvoiceResult issueResult(Object response, String orderNo) {
        if (!isSuccess(response)) {
            return failed(response, "ISSUE_FAILED");
        }
        Map<String, Object> data = responseData(response);
        String serialNo = str(data, "serialNo");
        return InvoiceResult.accepted(firstNonBlank(serialNo, orderNo), true);
    }

    /** 查单：按文档 status 归一；只有拿到票号且完成才 SUCCEEDED。 */
    public static InvoiceResult queryResult(Object response, String orderNo) {
        if (!isSuccess(response)) {
            return InvoiceResult.accepted(orderNo, true);
        }
        Map<String, Object> data = responseFirst(response, "response");
        if (data == null || data.isEmpty()) {
            return InvoiceResult.accepted(orderNo, true);
        }
        String invoiceNo = firstNonBlank(str(data, "invoiceNo"), str(data, "digitInvoiceNo"));
        String channelOrderNo = firstNonBlank(str(data, "serialNo"), str(data, "orderNo"), orderNo);
        String status = str(data, "status");

        if (BaiwangSpec.ST_SUCCESS.equals(status) && StringUtils.isNotBlank(invoiceNo)) {
            return InvoiceResult.succeeded(channelOrderNo, invoiceNo, str(data, "invoiceCode"),
                    parseDate(str(data, "invoiceDate")), filesOf(data))
                    .setAmount(decimal(data.get("invoiceTotalPriceTax")))
                    .setTaxAmount(decimal(data.get("invoiceTotalTax")));
        }
        if (BaiwangSpec.ST_FAILED.equals(status) || BaiwangSpec.ST_VOIDED.equals(status)) {
            return InvoiceResult.failed(nullTo(status, "QUERY_FAILED"),
                    firstNonBlank(str(data, "statusMessage"), str(data, "errorMessage"), "渠道返回开票失败/作废"));
        }
        return InvoiceResult.accepted(channelOrderNo, true);
    }

    /** 快捷冲红受理：异步，返回申请流水号并等待确认。 */
    public static RedApplyResult fastRedResult(Object response, String orderNo) {
        if (!isSuccess(response)) {
            return RedApplyResult.failed(nullTo(errorCode(response), "FAST_RED_FAILED"),
                    nullTo(errorMessage(response), "渠道调用失败"));
        }
        Map<String, Object> data = responseData(response);
        String serialNo = firstNonBlank(str(data, "redSerialNo"), str(data, "serialNo"), orderNo);
        String confirmNo = firstNonBlank(str(data, "redConfirmSerialNo"), str(data, "redInfoSerialNo"));
        RedApplyResult result = RedApplyResult.waiting(serialNo,
                java.time.LocalDateTime.now().plusHours(BaiwangSpec.RED_CONFIRM_HOURS));
        if (StringUtils.isNotBlank(confirmNo)) {
            result.setRedConfirmNo(confirmNo);
        }
        return result;
    }

    /** 确认单查询：按 redConfirmStatus 归一。 */
    public static RedApplyResult redConfirmResult(Object response, String confirmNo) {
        if (!isSuccess(response)) {
            return RedApplyResult.failed(nullTo(errorCode(response), "RED_CONFIRM_QUERY_FAILED"),
                    nullTo(errorMessage(response), "渠道调用失败"));
        }
        Map<String, Object> list = responseFirst(response, "response");
        Map<String, Object> first = firstOfList(list, "list");
        if (first == null) {
            return RedApplyResult.waiting(confirmNo,
                    java.time.LocalDateTime.now().plusHours(BaiwangSpec.RED_CONFIRM_HOURS));
        }
        String status = str(first, "redConfirmStatus");
        String returnedNo = firstNonBlank(str(first, "redConfirmNo"), confirmNo);
        if (BaiwangSpec.RED_CONFIRMED.equals(status)) {
            return RedApplyResult.confirmed(returnedNo);
        }
        if (BaiwangSpec.RED_REJECTED.equals(status)) {
            return RedApplyResult.failed(nullTo(status, "RED_REJECTED"),
                    nullTo(str(first, "redConfirmMessage"), "红字确认单被拒绝/作废"));
        }
        return RedApplyResult.waiting(returnedNo,
                java.time.LocalDateTime.now().plusHours(BaiwangSpec.RED_CONFIRM_HOURS));
    }

    /** 确认单操作结果：成功按已确认，失败按否认。 */
    public static RedApplyResult redOperateResult(Object response, String confirmNo, boolean accept) {
        if (!isSuccess(response)) {
            return RedApplyResult.failed(nullTo(errorCode(response), "RED_CONFIRM_OPERATE_FAILED"),
                    nullTo(errorMessage(response), "渠道调用失败"));
        }
        if (accept) {
            return RedApplyResult.confirmed(confirmNo);
        }
        return new RedApplyResult().setState(RedApplyState.REJECTED).setRedConfirmNo(confirmNo);
    }

    /** 响应是否成功：读文档公共参数 success。 */
    public static boolean isSuccess(Object response) {
        Map<String, Object> root = JsonUtils.toMap(response);
        Object success = root.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }
        return success != null && Boolean.parseBoolean(String.valueOf(success));
    }

    /** 回调 type → 框架状态：01 成功 / 02 失败；其余（如 05 删除）不迁移返回 null。 */
    public static InvoiceState stateOfCallbackType(String type) {
        if (BaiwangSpec.CALLBACK_SUCCESS.equals(type)) {
            return InvoiceState.SUCCEEDED;
        }
        if (BaiwangSpec.CALLBACK_FAILED.equals(type)) {
            return InvoiceState.FAILED;
        }
        return null;
    }

    private static InvoiceResult failed(Object response, String fallback) {
        return InvoiceResult.failed(nullTo(errorCode(response), fallback),
                nullTo(errorMessage(response), "渠道调用失败"));
    }

    public static String errorCode(Object response) {
        Map<String, Object> err = responseFirst(response, "errorResponse");
        return err == null ? null : firstNonBlank(str(err, "subCode"), str(err, "code"));
    }

    public static String errorMessage(Object response) {
        Map<String, Object> err = responseFirst(response, "errorResponse");
        return err == null ? null : firstNonBlank(str(err, "subMessage"), str(err, "message"));
    }

    /** 从查单响应提取版式文件，供版式查询能力复用。 */
    public static List<InvoiceFile> queryFiles(Object response) {
        if (!isSuccess(response)) {
            return Collections.emptyList();
        }
        Map<String, Object> data = responseFirst(response, "response");
        return data == null || data.isEmpty() ? Collections.<InvoiceFile>emptyList() : filesOf(data);
    }

    /** 文件地址：文档查询响应 xmlUrl/pdfUrl/ofdUrl/deliverShortUrl。 */
    private static List<InvoiceFile> filesOf(Map<String, Object> data) {
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        addUrl(files, InvoiceFileType.XML, str(data, "xmlUrl"));
        addUrl(files, InvoiceFileType.PDF, str(data, "pdfUrl"));
        addUrl(files, InvoiceFileType.OFD, str(data, "ofdUrl"));
        return files;
    }

    private static void addUrl(List<InvoiceFile> files, InvoiceFileType type, String url) {
        if (StringUtils.isNotBlank(url)) {
            files.add(new InvoiceFile().setType(type).setUrl(url));
        }
    }

    // ==================== 工具 ====================

    /** 票种 → 文档 invoiceTypeCode，可由 attrs.fplxdm.&lt;票种&gt; 覆盖。 */
    public static String invoiceTypeCode(BaiwangParams params, InvoiceType type) {
        if (params != null && params.getAttrs() != null && type != null) {
            String override = params.getAttrs().get("fplxdm." + type.name());
            if (StringUtils.isNotBlank(override)) {
                return override;
            }
        }
        return BaiwangSpec.invoiceTypeCode(type);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseData(Object response) {
        return responseFirst(response, "response");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseFirst(Object response, String key) {
        Map<String, Object> root = JsonUtils.toMap(response);
        Object value = root.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() || !(list.get(0) instanceof Map)
                    ? Collections.<String, Object>emptyMap()
                    : (Map<String, Object>) list.get(0);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstOfList(Map<String, Object> data, String key) {
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() || !(list.get(0) instanceof Map)
                    ? null : (Map<String, Object>) list.get(0);
        }
        return null;
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

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text) || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
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

    private static String nullTo(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }
}
