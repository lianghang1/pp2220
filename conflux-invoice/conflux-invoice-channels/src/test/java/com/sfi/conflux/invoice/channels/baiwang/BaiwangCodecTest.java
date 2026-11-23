package com.sfi.conflux.invoice.channels.baiwang;

import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.channels.baiwang.support.BaiwangCodec;
import com.sfi.conflux.invoice.model.InvoiceFile;
import com.sfi.conflux.invoice.model.InvoiceFileType;
import com.sfi.conflux.invoice.model.InvoiceState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百望云编解码回归：按文档响应字段名（Map）验证状态归一与文件提取。
 */
class BaiwangCodecTest {

    @Test
    void issueSuccessOnlyReturnsSerialNoStaysProcessing() {
        Map<String, Object> resp = successResp(single("serialNo", "BW-SERIAL"));
        InvoiceResult result = BaiwangCodec.issueResult(resp, "ORD-1");
        assertThat(result.getState()).isEqualTo(InvoiceState.PROCESSING);
        assertThat(result.getChannelOrderNo()).isEqualTo("BW-SERIAL");
        assertThat(result.isAsyncAccepted()).isTrue();
    }

    @Test
    void issueFailedReturnsFailed() {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("subCode", "E1001");
        err.put("subMessage", "购方名称不能为空");
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", false);
        resp.put("errorResponse", err);

        InvoiceResult result = BaiwangCodec.issueResult(resp, "ORD-2");
        assertThat(result.getState()).isEqualTo(InvoiceState.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("E1001");
        assertThat(result.getErrorMsg()).isEqualTo("购方名称不能为空");
    }

    @Test
    void querySuccessWithInvoiceNoBecomesSucceeded() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("status", BaiwangSpec.ST_SUCCESS);
        data.put("serialNo", "BW-SERIAL");
        data.put("orderNo", "ORD-3");
        data.put("invoiceNo", "24652000000012345678");
        data.put("invoiceCode", "033002600111");
        data.put("invoiceDate", "2026-11-17 10:30:00");
        data.put("invoiceTotalPriceTax", "113.00");
        data.put("invoiceTotalTax", "13.00");
        data.put("xmlUrl", "https://example.com/invoice.xml");
        data.put("pdfUrl", "https://example.com/invoice.pdf");

        InvoiceResult result = BaiwangCodec.queryResult(listResp(data), "ORD-3");
        assertThat(result.getState()).isEqualTo(InvoiceState.SUCCEEDED);
        assertThat(result.getInvoiceNo()).isEqualTo("24652000000012345678");
        assertThat(result.getInvoiceCode()).isEqualTo("033002600111");
        assertThat(result.getAmount()).isEqualByComparingTo("113.00");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("13.00");
        assertThat(result.getFiles()).extracting(InvoiceFile::getType)
                .contains(InvoiceFileType.XML, InvoiceFileType.PDF);
    }

    @Test
    void queryStatusMapping() {
        assertThat(BaiwangCodec.queryResult(queryResp(BaiwangSpec.ST_SUCCESS, "FP001"),
                "ORD-4").getState()).isEqualTo(InvoiceState.SUCCEEDED);
        assertThat(BaiwangCodec.queryResult(queryResp(BaiwangSpec.ST_PROCESSING, null),
                "ORD-5").getState()).isEqualTo(InvoiceState.PROCESSING);
        assertThat(BaiwangCodec.queryResult(queryResp(BaiwangSpec.ST_FAILED, null),
                "ORD-6").getState()).isEqualTo(InvoiceState.FAILED);
        assertThat(BaiwangCodec.queryResult(queryResp(BaiwangSpec.ST_SUCCESS, null),
                "ORD-7").getState()).isEqualTo(InvoiceState.PROCESSING);
    }

    @Test
    void queryFailureDoesNotOverturnInFlight() {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", "100002");
        err.put("message", "token不正确");
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", false);
        resp.put("errorResponse", err);

        InvoiceResult result = BaiwangCodec.queryResult(resp, "ORD-8");
        assertThat(result.getState()).isEqualTo(InvoiceState.PROCESSING);
    }

    @Test
    void callbackTypeMapping() {
        assertThat(BaiwangCodec.stateOfCallbackType(BaiwangSpec.CALLBACK_SUCCESS))
                .isEqualTo(InvoiceState.SUCCEEDED);
        assertThat(BaiwangCodec.stateOfCallbackType(BaiwangSpec.CALLBACK_FAILED))
                .isEqualTo(InvoiceState.FAILED);
        assertThat(BaiwangCodec.stateOfCallbackType("99")).isNull();
    }

    @Test
    void invoiceTypeCodeMappingAndOverride() {
        BaiwangParams params = new BaiwangParams();
        assertThat(BaiwangCodec.invoiceTypeCode(params,
                com.sfi.conflux.invoice.model.InvoiceType.DIGITAL_VAT_SPECIAL))
                .isEqualTo("01");
        params.setAttr(java.util.Collections.singletonMap(
                "fplxdm.DIGITAL_VAT_SPECIAL", "099"));
        assertThat(BaiwangCodec.invoiceTypeCode(params,
                com.sfi.conflux.invoice.model.InvoiceType.DIGITAL_VAT_SPECIAL))
                .isEqualTo("099");
    }

    private static Map<String, Object> queryResp(String status, String invoiceNo) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("status", status);
        data.put("serialNo", "BW-SERIAL");
        if (invoiceNo != null) {
            data.put("invoiceNo", invoiceNo);
        }
        return listResp(data);
    }

    private static Map<String, Object> listResp(Map<String, Object> first) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", true);
        resp.put("response", new java.util.ArrayList<Map<String, Object>>(
                java.util.Collections.singletonList(first)));
        return resp;
    }

    private static Map<String, Object> successResp(Map<String, Object> data) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", true);
        resp.put("response", data);
        return resp;
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }
}
