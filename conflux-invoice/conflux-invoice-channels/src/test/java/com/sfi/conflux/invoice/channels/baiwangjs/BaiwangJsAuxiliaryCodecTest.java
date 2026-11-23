package com.sfi.conflux.invoice.channels.baiwangjs;

import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.api.RepairResult;
import com.sfi.conflux.invoice.channel.InvoiceContext;
import com.sfi.conflux.invoice.channels.baiwangjs.support.BaiwangJsCodec;
import com.sfi.conflux.invoice.channels.baiwangjs.support.BaiwangJsResponse;
import com.sfi.conflux.invoice.model.Invoice;
import com.sfi.conflux.invoice.model.InvoiceFile;
import com.sfi.conflux.invoice.model.InvoiceFileType;
import com.sfi.conflux.invoice.model.InvoiceItem;
import com.sfi.conflux.invoice.model.InvoiceState;
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
 * <p>全部为纯函数级验证，不发任何网络请求；字段命名以官方公开文档为准。</p>
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

    // ---- 3.2.7 查单状态归一（与回调语义不同：2=成功 3=失败 8=未生成PDF 1=开票中）----

    @Test
    void queryInvoiceResult_status2WithXml_succeeded() {
        BaiwangJsResponse ok = response("{\"code\":\"0\",\"data\":{\"status\":\"2\","
                + "\"qdfphm\":\"" + FPHM + "\",\"xmlUrl\":\"https://f.example/x.xml\","
                + "\"pdf\":\"https://f.example/x.pdf\"}}");
        InvoiceResult r = BaiwangJsCodec.queryInvoiceResult(ok, "ORDER-Q1");
        assertSame(InvoiceState.SUCCEEDED, r.getState());
        assertEquals(FPHM, r.getInvoiceNo());
        assertTrue(hasFile(r.getFiles(), InvoiceFileType.XML, "https://f.example/x.xml"));
    }

    @Test
    void queryInvoiceResult_status2WithoutXml_staysProcessing() {
        // 文档 3.2.7 查单成功一般只回 pdf；缺 XML 法定原件时保持挂起，等 qdbswj 版式回调
        BaiwangJsResponse ok = response("{\"code\":\"0\",\"data\":{\"status\":\"2\","
                + "\"qdfphm\":\"" + FPHM + "\",\"pdf\":\"https://f.example/x.pdf\"}}");
        InvoiceResult r = BaiwangJsCodec.queryInvoiceResult(ok, "ORDER-Q2");
        assertSame(InvoiceState.PROCESSING, r.getState());
    }

    @Test
    void queryInvoiceResult_status3_failed() {
        // 关键回归：3.2.7 里 2=成功、3=失败；不能再用回调语义把成功票判死
        BaiwangJsResponse bad = response("{\"code\":\"0\",\"data\":{\"status\":\"3\","
                + "\"ycyy\":\"库存不足\"}}");
        InvoiceResult r = BaiwangJsCodec.queryInvoiceResult(bad, "ORDER-Q3");
        assertSame(InvoiceState.FAILED, r.getState());
        assertEquals("QUERY_FAILED", r.getErrorCode());
        assertEquals("库存不足", r.getErrorMsg());
    }

    @Test
    void queryInvoiceResult_status1IsProcessing_notSucceeded() {
        // 3.2.7 里 1=开票中；回调语义的 1=成功 不适用于查单
        BaiwangJsResponse mid = response("{\"code\":\"0\",\"data\":{\"status\":\"1\"}}");
        InvoiceResult r = BaiwangJsCodec.queryInvoiceResult(mid, "ORDER-Q4");
        assertSame(InvoiceState.PROCESSING, r.getState());
    }

    @Test
    void queryInvoiceResult_status8And6() {
        BaiwangJsResponse noPdf = response(
                "{\"code\":\"0\",\"data\":{\"status\":\"8\"}}");
        assertSame(InvoiceState.PROCESSING,
                BaiwangJsCodec.queryInvoiceResult(noPdf, "ORDER-Q5").getState());
        BaiwangJsResponse deleted = response(
                "{\"code\":\"0\",\"data\":{\"status\":\"6\",\"ycyy\":\"已作废\"}}");
        InvoiceResult r = BaiwangJsCodec.queryInvoiceResult(deleted, "ORDER-Q6");
        assertSame(InvoiceState.FAILED, r.getState());
        assertEquals("INVOICE_DELETED", r.getErrorCode());
    }

    // ---- 3.2.6 特殊票种 / 特定业务要素 / 免税明细 / 交付字段 ----

    @Test
    void issueBody_constructionSpecialBiz_forcesTspz03AndDropsDjSl() {
        Invoice invoice = baseIssueInvoice();
        Map<String, String> jzfw = new java.util.LinkedHashMap<String, String>();
        jzfw.put("tdzzsxmbh", "TDZS-001");
        jzfw.put("jzfwfsd", "上海市浦东新区");
        jzfw.put("jzxmmc", "园区项目");
        jzfw.put("kdsbz", "Y");
        invoice.setJzfwTdys(jzfw);

        Map<String, Object> body = BaiwangJsCodec.issueBody(issueCtx(invoice), params(), "ORDER-JZ");
        assertEquals("03", body.get("tspz"), "建筑服务要素存在时自动推断 tspz=03");
        assertEquals("TDZS-001", ((Map<?, ?>) body.get("jzfwTdys")).get("tdzzsxmbh"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertFalse(lines.get(0).containsKey("dj"), "建筑服务明细 dj 必须为空");
        assertFalse(lines.get(0).containsKey("sl"), "建筑服务明细 sl 必须为空");
        assertTrue(lines.get(0).containsKey("ggxh"));
    }

    @Test
    void issueBody_realEstateSpecialBiz_forcesTspz06AndDropsGgxh() {
        Invoice invoice = baseIssueInvoice();
        Map<String, String> bdc = new java.util.LinkedHashMap<String, String>();
        bdc.put("bdcdz", "上海市浦东新区XX路1号");
        bdc.put("zlqqz", "2024-01-01 2024-12-31");
        bdc.put("kdsbz", "Y");
        bdc.put("cqzsh", "沪(2024)字第001号");
        bdc.put("dw", "月");
        invoice.setBdcTdysList(java.util.Collections.singletonList(bdc));

        Map<String, Object> body = BaiwangJsCodec.issueBody(issueCtx(invoice), params(), "ORDER-BDC");
        assertEquals("06", body.get("tspz"), "不动产要素存在时自动推断 tspz=06");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertFalse(lines.get(0).containsKey("ggxh"), "不动产明细 ggxh 必须为空");
        assertTrue(lines.get(0).containsKey("dj"), "不动产明细 dj 必填");
        assertTrue(lines.get(0).containsKey("sl"), "不动产明细 sl 必填");
    }

    @Test
    void issueBody_taxExemptItemAndBuyerDelivery() {
        Invoice invoice = baseIssueInvoice();
        invoice.getItems().get(0).setLslbs("1");
        invoice.getItems().get(0).setYhzcbs("1");
        invoice.getItems().get(0).setZzstsgl("免税");
        invoice.setBuyerMobile("13800000000");
        invoice.setBuyerEmail("a@b.com,c@d.com");

        Map<String, Object> body = BaiwangJsCodec.issueBody(issueCtx(invoice), params(), "ORDER-MJ");
        assertEquals("13800000000", body.get("gmfMobile"));
        assertEquals("a@b.com,c@d.com", body.get("gmfEmail"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals("1", lines.get(0).get("lslbs"));
        assertEquals("1", lines.get(0).get("yhzcbs"));
        assertEquals("免税", lines.get(0).get("zzstsgl"), "yhzcbs=1 时 zzstsgl 必填");
    }

    @Test
    void issueBody_defaultsNormalTaxAndExplicitTspzWins() {
        Invoice invoice = baseIssueInvoice();
        invoice.setTspz("01");
        invoice.setJzfwTdys(java.util.Collections.<String, String>emptyMap());

        Map<String, Object> body = BaiwangJsCodec.issueBody(issueCtx(invoice), params(), "ORDER-X");
        assertEquals("01", body.get("tspz"), "显式 tspz 优先于要素推断");
        assertFalse(body.containsKey("jzfwTdys"), "空要素不输出节点");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals("0", lines.get(0).get("lslbs"), "未指定时按正常税率上报");
        assertEquals("0", lines.get(0).get("yhzcbs"));
        assertFalse(lines.get(0).containsKey("zzstsgl"));
    }

    // ---- 3.3.3 红冲申请必填字段（ykprq / yfplxdm / sfwzzfp / chyydm / lzmxxh）----

    @Test
    void redApplyBody_carriesRequiredRedFields() {
        Invoice invoice = baseIssueInvoice();
        invoice.setInvoiceTime(java.time.LocalDateTime.of(2024, 6, 6, 18, 31, 52));

        Map<String, Object> body = BaiwangJsCodec.redApplyBody(issueCtx(invoice), params(), "RED-9");
        assertEquals("2024-06-06 18:31:52", body.get("ykprq"), "数电原开票日期格式");
        assertEquals("01", body.get("yfplxdm"), "原发票类型必填");
        assertEquals("N", body.get("sfwzzfp"), "数电票固定 N");
        assertEquals("1", body.get("chyydm"), "未指定时默认开票有误（全额红冲）");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("detailParam");
        assertEquals(1, lines.get(0).get("lzmxxh"), "红字明细须带原蓝字行号");
    }

    @Test
    void redApplyBody_chyydmFromApply() {
        Invoice invoice = baseIssueInvoice();
        com.sfi.conflux.invoice.model.RedApply apply =
                new com.sfi.conflux.invoice.model.RedApply();
        apply.setChyydm("2");
        InvoiceContext ctx = issueCtx(invoice).setRedApply(apply);

        Map<String, Object> body = BaiwangJsCodec.redApplyBody(ctx, params(), "RED-10");
        assertEquals("2", body.get("chyydm"), "销货退回（支持部分红冲）应透传");
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

    private static boolean hasFile(List<InvoiceFile> files, InvoiceFileType type, String url) {
        return has(files, type, url);
    }

    private static BaiwangJsResponse response(String body) {
        return BaiwangJsCodec.parseResponse(body);
    }

    private static Invoice baseIssueInvoice() {
        InvoiceItem item = new InvoiceItem()
                .setLineNo(1).setItemName("技术服务费")
                .setSpecification("规格A").setUnit("次")
                .setTaxClassificationCode("3040407990000000000")
                .setAmount(new java.math.BigDecimal("113.00"))
                .setTaxAmount(new java.math.BigDecimal("13.00"))
                .setTaxRate(new java.math.BigDecimal("0.13"))
                .setQuantity(java.math.BigDecimal.ONE)
                .setUnitPrice(new java.math.BigDecimal("113.00"));
        return new Invoice()
                .setInvoiceId("INV-A").setConfigId("demo")
                .setSellerTaxNo(TAX).setSellerName("测试销方")
                .setBuyerTitle("测试购方").setBuyerTaxNo("91310000BUYER00001")
                .setBuyerAddress("BuyerAddr").setBuyerPhone("021-7777")
                .setBuyerBank("BuyerBank")
                .setInvoiceType(com.sfi.conflux.invoice.model.InvoiceType.DIGITAL_VAT_SPECIAL)
                .setItems(java.util.Collections.singletonList(item))
                .setAmount(new java.math.BigDecimal("133.00"))
                .setTaxAmount(new java.math.BigDecimal("13.00"))
                .setInvoiceNo(FPHM).setInvoiceCode(FPDM)
                .setState(com.sfi.conflux.invoice.model.InvoiceState.PROCESSING);
    }

    private static InvoiceContext issueCtx(Invoice invoice) {
        BaiwangJsParams config = params();
        config.setConfigId("demo").setChannelCode(BaiwangJsSpec.CODE)
                .setSellerTaxNo(invoice.getSellerTaxNo())
                .setAttr(java.util.Collections.<String, String>emptyMap());
        return com.sfi.conflux.invoice.channel.InvoiceContext
                .of(config, null, invoice);
    }

    private static BaiwangJsParams params() {
        BaiwangJsParams p = new BaiwangJsParams()
                .setGatewayKey("gw-key").setGatewaySecret("secret")
                .setQyId("Q1").setDlzh("13800000000");
        p.setSellerTaxNo(TAX);
        return p;
    }
}
