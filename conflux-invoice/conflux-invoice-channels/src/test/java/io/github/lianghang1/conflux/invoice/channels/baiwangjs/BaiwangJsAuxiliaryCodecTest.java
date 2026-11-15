package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsCodec;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsResponse;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A+B 档新增报文 / 响应归一单测：3.1.1、3.3.12/15/16/17、3.4.5/7/8/11/12/13/15。
 *
 * <p>全部为纯函数级验证，不发任何网络请求；字段命名以用户指定语雀文档为准。</p>
 *
 * @author lianghang
 */
class BaiwangJsAuxiliaryCodecTest {

    private static final String TAX = "91310000TEST000001";
    private static final String FPHM = "24312000000000000001";
    private static final String FPDM = "144031609110";

    // ---- 3.1.1 查询企业唯一标识 ----

    @Test
    void uniqueSignBody_onlyNeedsNameAndTaxNo() {
        Map<String, Object> body = BaiwangJsCodec.uniqueSignBody("测试企业", TAX);
        assertEquals(2, body.size());
        assertEquals("测试企业", body.get("nsrmc"));
        assertEquals(TAX, body.get("nsrsbh"));
    }

    // ---- 3.4.7 发票短信邮箱重发 ----

    @Test
    void deliveryBody_carriesTicketAndBothTargets() {
        Map<String, Object> body = BaiwangJsCodec.deliveryBody(params(), FPDM, FPHM,
                "13800000000", "a@b.com,c@d.com");
        assertEquals("Q1", body.get("qyId"));
        assertEquals(FPDM, body.get("fpDm"));
        assertEquals(FPHM, body.get("fpHm"));
        assertEquals(FPHM, body.get("qdfphm"));
        assertEquals("13800000000", body.get("phone"));
        assertEquals("a@b.com,c@d.com", body.get("email"));
    }

    @Test
    void deliveryBody_omitsBlankOptionals() {
        Map<String, Object> body = BaiwangJsCodec.deliveryBody(params(), null, FPHM,
                "13800000000", null);
        assertFalse(body.containsKey("fpDm"));
        assertFalse(body.containsKey("email"));
        assertEquals(FPHM, body.get("qdfphm"));
    }

    // ---- 3.4.8 电子发票下载地址查询 ----

    @Test
    void downloadBody_onlyQyIdAndDdlsh() {
        Map<String, Object> body = BaiwangJsCodec.downloadBody(params(), "ORDER-1");
        assertEquals(2, body.size());
        assertEquals("Q1", body.get("qyId"));
        assertEquals("ORDER-1", body.get("ddlsh"));
    }

    // ---- 3.4.15 电票版式文件查询 ----

    @Test
    void layoutQueryBody_byInvoiceNo() {
        Map<String, Object> body = BaiwangJsCodec.layoutQueryBody(params(), "ORDER-1", FPDM, FPHM);
        assertEquals("Q1", body.get("qyId"));
        assertEquals("ORDER-1", body.get("ddlsh"));
        assertEquals(FPDM, body.get("fpdm"));
        assertEquals(FPHM, body.get("fphm"));
        assertEquals(FPHM, body.get("qdfphm"));
        assertEquals("13800000000", body.get("dlzh"));
    }

    // ---- 3.4.11 发票修复 ----

    @Test
    void repairBody_cxfsByNumberWhenNoDate() {
        Map<String, Object> body = BaiwangJsCodec.repairBody(params(), "REPAIR-1", "01",
                FPHM, null, null, null);
        assertEquals("0", body.get("cxfs"));
        assertEquals("01", body.get("fplxdm"));
        assertEquals(FPHM, body.get("fphm"));
        assertEquals("Q1", body.get("qyId"));
        assertEquals(TAX, body.get("nsrsbh"));
        assertEquals(TAX, body.get("sksbh"));
        assertFalse(body.containsKey("qsrq"));
        assertFalse(body.containsKey("zzrq"));
    }

    @Test
    void repairBody_cxfsByDateWhenDateRangeGiven() {
        Map<String, Object> body = BaiwangJsCodec.repairBody(params(), "REPAIR-2", "01",
                null, "2026-01-01", "2026-01-31", "测试购方");
        assertEquals("3", body.get("cxfs"));
        assertEquals("2026-01-01", body.get("qsrq"));
        assertEquals("2026-01-31", body.get("zzrq"));
        assertEquals("测试购方", body.get("gmfMc"));
        assertFalse(body.containsKey("fphm"));
    }

    // ---- 3.4.12 发票修复结果查询 ----

    @Test
    void repairResultBody_elecBzDefaultAndOverride() {
        assertEquals("1", BaiwangJsCodec.repairResultBody(params(), "REPAIR-1", null)
                .get("elecBz"));
        Map<String, Object> body = BaiwangJsCodec.repairResultBody(params(), "REPAIR-1", "0");
        assertEquals("0", body.get("elecBz"));
        assertEquals("REPAIR-1", body.get("ddlsh"));
    }

    // ---- 3.4.13 发票查询-按票号或时间 ----

    @Test
    void queryBatchBody_defaultsAndNumberRange() {
        Map<String, Object> body = BaiwangJsCodec.queryBatchBody(params(), "0", "01",
                FPDM, FPHM, "24312000000000000010", null, null, null, null, null);
        assertEquals("0", body.get("cxfs"));
        assertEquals("01", body.get("fplxdm"));
        assertEquals(FPDM, body.get("fpdm"));
        assertEquals(FPHM, body.get("qshm"));
        assertEquals("24312000000000000010", body.get("zzhm"));
        assertEquals("1", body.get("elecBz"));
        assertEquals("0", body.get("pageNo"));
        assertEquals("50", body.get("pageSize"));
    }

    @Test
    void queryBatchBody_dateModeAndPaging() {
        Map<String, Object> body = BaiwangJsCodec.queryBatchBody(params(), "3", null,
                null, null, null, "2026-01-01", "2026-01-31", FPHM, "2", "10");
        assertEquals("3", body.get("cxfs"));
        assertEquals("2026-01-01", body.get("qsrq"));
        assertEquals("2026-01-31", body.get("zzrq"));
        assertEquals(FPHM, body.get("qdfphm"));
        assertEquals("2", body.get("pageNo"));
        assertEquals("10", body.get("pageSize"));
    }

    // ---- 3.4.5 获取实人认证二维码（异步） ----

    @Test
    void qrCodeAsyncBody_defaultEwmlx() {
        assertEquals("1", BaiwangJsCodec.qrCodeAsyncBody(params(), "ORDER-1", null)
                .get("ewmlx"));
        assertEquals("2", BaiwangJsCodec.qrCodeAsyncBody(params(), "ORDER-1", "2")
                .get("ewmlx"));
    }

    // ---- 3.3.12 / 3.3.15 / 3.3.16 红冲两段 ----

    @Test
    void redIssueAndStatusCarryConfirmNo() {
        Map<String, Object> issue = BaiwangJsCodec.redIssueBody(params(), "RED-1", "HZ001");
        assertEquals("RED-1", issue.get("ddlsh"));
        assertEquals("HZ001", issue.get("hzfpxxqrdbh"));
        Map<String, Object> status = BaiwangJsCodec.redStatusBody(params(), "RED-1", "HZ001");
        assertEquals("HZ001", status.get("hzfpxxqrdbh"));
    }

    @Test
    void redConfirmBody_qrlxAcceptOrReject() {
        Map<String, Object> accept = BaiwangJsCodec.redConfirmBody(params(), "HZ001", true);
        assertEquals("Y", accept.get("qrlx"));
        assertEquals("Q1", accept.get("qyId"));
        assertEquals("HZ001", accept.get("hzfpxxqrdbh"));
        assertEquals("N", BaiwangJsCodec.redConfirmBody(params(), "HZ001", false)
                .get("qrlx"));
    }

    // ---- 3.3.17 可冲销明细查询 ----

    @Test
    void redAvailableBody_carriesOriginalAndDefaults() {
        Invoice original = new Invoice().setSellerTaxNo(TAX)
                .setBuyerTaxNo("91310000BUYER00001")
                .setInvoiceNo(FPHM).setInvoiceCode(FPDM);
        Map<String, Object> body = BaiwangJsCodec.redAvailableBody(params(), "HZCX-1",
                original, "2026-01-15", null, "2");
        assertEquals("2", body.get("fplyDm"));
        assertEquals("1", body.get("gjbq"));
        assertEquals(FPHM, body.get("lzfphm"));
        assertEquals(FPDM, body.get("lzfpdm"));
        assertEquals("91310000BUYER00001", body.get("gmfnsrsbh"));
        assertEquals("2026-01-15", body.get("lzkprq"));
    }

    // ---- 版式文件 / data 节点 ----

    @Test
    void layoutFiles_compatibleNamingSets() {
        List<InvoiceFile> camel = BaiwangJsCodec.layoutFiles(JsonUtils.readTree(
                "{\"pdfUrl\":\"p\",\"ofdUrl\":\"o\",\"xmlUrl\":\"x\"}"));
        assertEquals(3, camel.size());
        assertTrue(has(camel, InvoiceFileType.XML, "x"));
        assertTrue(has(camel, InvoiceFileType.OFD, "o"));
        assertTrue(has(camel, InvoiceFileType.PDF, "p"));

        List<InvoiceFile> legacy = BaiwangJsCodec.layoutFiles(JsonUtils.readTree(
                "{\"pdf\":\"p2\",\"ofdurl\":\"o2\",\"xmlurl\":\"x2\"}"));
        assertEquals(3, legacy.size());
        assertTrue(has(legacy, InvoiceFileType.XML, "x2"));

        List<InvoiceFile> gs = BaiwangJsCodec.layoutFiles(JsonUtils.readTree(
                "{\"gsPdf\":\"p3\",\"gsOfdUrl\":\"o3\"}"));
        assertEquals(2, gs.size());
        assertTrue(has(gs, InvoiceFileType.OFD, "o3"));
        assertTrue(has(gs, InvoiceFileType.PDF, "p3"));
    }

    @Test
    void dataNode_supportsDataAndCapitalData() {
        BaiwangJsResponse lower = BaiwangJsCodec.parseResponse(
                "{\"code\":\"0\",\"data\":{\"qyId\":\"Q-LOWER\"}}");
        assertEquals("Q-LOWER", BaiwangJsCodec.text(BaiwangJsCodec.dataNode(lower), "qyId"));
        BaiwangJsResponse upper = BaiwangJsCodec.parseResponse(
                "{\"code\":\"0\",\"Data\":{\"qyId\":\"Q-UPPER\"}}");
        assertEquals("Q-UPPER", BaiwangJsCodec.text(BaiwangJsCodec.dataNode(upper), "qyId"));
        assertNull(BaiwangJsCodec.dataNode(
                BaiwangJsCodec.parseResponse("{\"code\":\"0\"}")));
    }

    // ---- 3.4.12 / 3.4.13 修复结果归一 ----

    @Test
    void repairResult_codeZeroWithDataNode_succeeded() {
        BaiwangJsResponse ok = BaiwangJsCodec.parseResponse(
                "{\"code\":\"0\",\"message\":\"成功\",\"data\":{"
                        + "\"fphm\":\"" + FPHM + "\",\"fpdm\":\"" + FPDM + "\","
                        + "\"pdfUrl\":\"p\",\"ofdUrl\":\"o\",\"xmlUrl\":\"x\"}}");
        RepairResult r = BaiwangJsCodec.repairResult(ok, "REPAIR-1");
        assertSame(RepairResult.RepairState.SUCCEEDED, r.getState());
        assertEquals("REPAIR-1", r.getChannelOrderNo());
        assertEquals(FPHM, r.getInvoiceNo());
        assertEquals(FPDM, r.getInvoiceCode());
        assertEquals(3, r.getFiles().size());
        assertEquals(ok.getRawBody(), r.getRawPayload());
    }

    @Test
    void repairResult_codeZeroWithRootNode_succeeded() {
        BaiwangJsResponse ok = BaiwangJsCodec.parseResponse(
                "{\"code\":\"0\",\"qdfphm\":\"" + FPHM + "\",\"xmlurl\":\"x\",\"pdf\":\"p\"}");
        RepairResult r = BaiwangJsCodec.repairResult(ok, "REPAIR-2");
        assertSame(RepairResult.RepairState.SUCCEEDED, r.getState());
        assertEquals(FPHM, r.getInvoiceNo());
        assertEquals(2, r.getFiles().size());
        assertTrue(has(r.getFiles(), InvoiceFileType.XML, "x"));
    }

    @Test
    void repairResult_minusOne_staysProcessing() {
        BaiwangJsResponse pending = BaiwangJsCodec.parseResponse(
                "{\"code\":\"-1\",\"message\":\"未修复到发票\"}");
        RepairResult r = BaiwangJsCodec.repairResult(pending, "REPAIR-3");
        assertSame(RepairResult.RepairState.PROCESSING, r.getState());
        assertEquals("REPAIR-3", r.getChannelOrderNo());
        assertTrue(r.getErrorMsg().contains("未修复到发票"));
        assertEquals(pending.getRawBody(), r.getRawPayload());
    }

    @Test
    void repairResult_otherCode_failed() {
        BaiwangJsResponse bad = BaiwangJsCodec.parseResponse(
                "{\"code\":\"1003\",\"message\":\"需要登录\",\"renson\":\"登录态过期\"}");
        RepairResult r = BaiwangJsCodec.repairResult(bad, "REPAIR-4");
        assertSame(RepairResult.RepairState.FAILED, r.getState());
        assertEquals("1003", r.getErrorCode());
        assertEquals("登录态过期", r.getErrorMsg());
    }

    @Test
    void repairResult_codeZeroWithoutFiles_stillSucceeded() {
        RepairResult r = BaiwangJsCodec.repairResult(
                BaiwangJsCodec.parseResponse("{\"code\":\"0\"}"), "REPAIR-5");
        assertSame(RepairResult.RepairState.SUCCEEDED, r.getState());
        assertTrue(r.getFiles().isEmpty());
    }

    // ---- 夹具 ----

    private static boolean has(List<InvoiceFile> files, InvoiceFileType type, String url) {
        for (InvoiceFile file : files) {
            if (file.getType() == type && url.equals(file.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private static BaiwangJsParams params() {
        return new BaiwangJsParams()
                .setGatewayKey("gw-key").setGatewaySecret("secret")
                .setQyId("Q1").setSellerTaxNo(TAX).setDlzh("13800000000");
    }
}
