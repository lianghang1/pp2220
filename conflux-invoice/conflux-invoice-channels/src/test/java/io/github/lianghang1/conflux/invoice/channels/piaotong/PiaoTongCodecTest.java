package io.github.lianghang1.conflux.invoice.channels.piaotong;

import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongCodec;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongResponse;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票通业务报文组装与结果归一测试（文档 2.9/2.11/2.15 字段表逐项核对）。
 */
class PiaoTongCodecTest {

    private static Invoice invoice() {
        Invoice invoice = new Invoice()
                .setBizOrderNo("BIZ-001")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setBuyerTitle("北京测试科技有限公司")
                .setBuyerTaxNo("911100000000000000")
                .setBuyerAddress("北京市海淀区测试路 1 号")
                .setBuyerPhone("010-1234567")
                .setBuyerBank("测试银行")
                .setRemark("备注")
                .setInvoiceDate(LocalDate.of(2024, 1, 8));
        InvoiceItem item = new InvoiceItem()
                .setLineNo(1)
                .setItemName("信息技术服务费")
                .setTaxClassificationCode("3040202000000000000")
                .setSpecification("规格A")
                .setUnit("次")
                .setQuantity(new BigDecimal("1"))
                .setUnitPrice(new BigDecimal("100"))
                .setAmount(new BigDecimal("100.00"))
                .setTaxRate(new BigDecimal("0.06"))
                .setTaxAmount(new BigDecimal("6.00"));
        invoice.setItems(Arrays.asList(item));
        return invoice;
    }

    @Test
    void invoiceKind_mapping() {
        assertEquals(PiaoTongSpec.KIND_DIGITAL_SPECIAL,
                PiaoTongCodec.invoiceKind(new Invoice().setInvoiceType(
                        InvoiceType.DIGITAL_VAT_SPECIAL)));
        assertEquals(PiaoTongSpec.KIND_DIGITAL_GENERAL,
                PiaoTongCodec.invoiceKind(new Invoice().setInvoiceType(
                        InvoiceType.DIGITAL_VAT_GENERAL)));
    }

    @Test
    void electronicNo_concatCodeAndNo() {
        Invoice invoice = new Invoice().setInvoiceCode("123456789012")
                .setInvoiceNo("12345678");
        assertEquals("12345678901212345678", PiaoTongCodec.electronicNo(invoice));
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("electronicInvoiceNo", "20990000000000000099");
        invoice.setAttr(attr);
        assertEquals("20990000000000000099", PiaoTongCodec.electronicNo(invoice));
    }

    @Test
    void issueBody_coreFields() {
        Invoice invoice = invoice();
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("takerEmail", "buyer@test.com");
        attr.put("buyerMobile", "13800000000");
        invoice.setAttr(attr);
        Map<String, Object> body = PiaoTongCodec.issueBody(invoice,
                "911100000000000000", "ABCD20240108000000+10");
        assertEquals("911100000000000000", body.get("taxpayerNum"));
        assertEquals("ABCD20240108000000+10", body.get("invoiceReqSerialNo"));
        assertEquals(PiaoTongSpec.KIND_DIGITAL_GENERAL, body.get("invoiceIssueKindCode"));
        assertEquals("北京测试科技有限公司", body.get("buyerName"));
        assertEquals("911100000000000000", body.get("buyerTaxpayerNum"));
        assertEquals("010-1234567", body.get("buyerTel"));
        assertEquals("buyer@test.com", body.get("takerEmail"));
        assertEquals("BIZ-001", body.get("tradeNo"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("itemList");
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("1", line.get("itemNo"));
        assertEquals("0", line.get("itemProperty"));
        assertEquals("信息技术服务费", line.get("goodsName"));
        assertEquals("3040202000000000000", line.get("taxClassificationCode"));
        assertEquals("1.00000000", line.get("quantity"));
        assertEquals("100.00000000", line.get("unitPrice"));
        assertEquals("100.00", line.get("invoiceAmount"));
        assertEquals("0.06", line.get("taxRateValue"));
        assertEquals("6.00", line.get("taxRateAmount"));
    }

    @Test
    void issueBody_zeroTaxLine_setsPreferentialFlag() {
        Invoice invoice = invoice();
        InvoiceItem item = invoice.getItems().get(0);
        item.setLslbs("1");
        item.setZzstsgl("免税");
        Map<String, Object> body = PiaoTongCodec.issueBody(invoice, "TAX", "S1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("itemList");
        assertEquals("1", lines.get(0).get("zeroTaxFlag"));
        assertEquals("1", lines.get(0).get("preferentialPolicyFlag"));
        assertEquals("免税", lines.get(0).get("vatSpecialManage"));
    }

    @Test
    void result_success_withPdfLayout() {
        PiaoTongResponse response = parse("{"
                + "\"code\":\"0000\",\"msg\":\"开具成功\","
                + "\"invoiceNo\":\"12345678\",\"invoiceCode\":\"123456789012\","
                + "\"invoiceDate\":\"2024-01-08 10:00:00\","
                + "\"amountWithTax\":\"106.00\",\"taxAmount\":\"6.00\","
                + "\"invoicePdf\":\"PDFBASE64\"}");
        InvoiceResult result = PiaoTongCodec.issueOrQueryResult(response, "S1");
        assertEquals(InvoiceState.SUCCEEDED, result.getState());
        assertEquals("12345678", result.getInvoiceNo());
        assertEquals("123456789012", result.getInvoiceCode());
        assertEquals(LocalDate.of(2024, 1, 8), result.getInvoiceDate());
        assertEquals(1, result.getFiles().size());
        assertEquals(InvoiceFileType.PDF, result.getFiles().get(0).getType());
    }

    @Test
    void result_failed_andPending() {
        assertEquals(InvoiceState.FAILED, PiaoTongCodec.issueOrQueryResult(
                parse("{\"code\":\"9999\",\"msg\":\"开票失败\"}"), "S1").getState());
        assertEquals(InvoiceState.PROCESSING, PiaoTongCodec.issueOrQueryResult(
                parse("{\"code\":\"7777\",\"msg\":\"开票中\"}"), "S1").getState());
        assertEquals(InvoiceState.PROCESSING, PiaoTongCodec.issueOrQueryResult(
                parse("{\"code\":\"5999\",\"msg\":\"索单审核中\"}"), "S1").getState());
        // 成功状态但无票号：保持挂起，等回调/下次查询
        assertEquals(InvoiceState.PROCESSING, PiaoTongCodec.issueOrQueryResult(
                parse("{\"code\":\"0000\"}"), "S1").getState());
        // 无业务报文 + 信封受理成功：保持挂起
        assertEquals(InvoiceState.PROCESSING, PiaoTongCodec.issueOrQueryResult(
                new PiaoTongResponse().setCode(PiaoTongSpec.ST_SUCCESS), "S1").getState());
    }

    @Test
    void fileContent_onlyOnSuccess() {
        assertEquals("FILEDATA", PiaoTongCodec.fileContent(parse(
                "{\"resultCode\":\"0000\",\"fileContent\":\"FILEDATA\"}")));
        assertTrue(PiaoTongCodec.fileContent(parse(
                "{\"resultCode\":\"9999\",\"fileContent\":\"X\"}")) == null);
    }

    @Test
    void voidStates() {
        for (String state : new String[]{"85", "86", "87", "88", "89", "90"}) {
            assertTrue(PiaoTongCodec.isVoidState(state), state);
        }
        assertTrue(!PiaoTongCodec.isVoidState("81"));
        assertTrue(!PiaoTongCodec.isVoidState("82"));
    }

    private static PiaoTongResponse parse(String businessJson) {
        PiaoTongResponse response = new PiaoTongResponse()
                .setCode(PiaoTongSpec.ST_SUCCESS)
                .setRawBody(businessJson);
        response.setRoot(JsonUtils.readTree(businessJson));
        return response;
    }
}
