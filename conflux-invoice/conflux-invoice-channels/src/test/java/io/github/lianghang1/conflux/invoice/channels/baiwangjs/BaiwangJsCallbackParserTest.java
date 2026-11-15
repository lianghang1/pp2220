package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsCallbackParser;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回调分单测：Base64 明文、ywlxdm 分流、status 映射、XML 法定原件、应答格式。
 *
 * @author lianghang
 */
class BaiwangJsCallbackParserTest {

    private static final String DDLSH = "ORDER-20260115-001";

    @Test
    void fpkj_success_onlySupplementsFaceFields_waitsForLayout() {
        InvoiceCallbackEvent event = parse(callback("{\n"
                + "  \"ywlxdm\":\"fpkj\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"qdfphm\":\"24312000000000000001\", \"fpdm\":\"\", \"fphm\":\"\",\n"
                + "  \"kprq\":\"20260115142522\", \"jshj\":\"133.00\", \"hjse\":\"13.00\"\n"
                + "}"));
        assertEquals(BaiwangJsSpec.YW_FPKJ, event.getAttrs().get("ywlxdm"));
        assertEquals(DDLSH, event.getBizOrderNo());
        assertEquals(DDLSH, event.getChannelOrderNo());
        assertEquals("24312000000000000001", event.getInvoiceNo());
        assertEquals(LocalDate.of(2026, 1, 15), event.getInvoiceDate());
        assertEquals("133.00", event.getAmount().toPlainString());
        assertNull(event.getState(), "成功结果等版式回调携带 XML 后才置 SUCCEEDED");
        assertEquals("true", event.getAttrs().get("faceFieldsOnly"));
        assertNull(event.getFiles());
    }

    @Test
    void qdbswj_success_carriesLegalXmlAndAdvancesToSucceeded() {
        InvoiceCallbackEvent event = parse(callback("{\n"
                + "  \"ywlxdm\":\"qdbswj\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"ofdurl\":\"https://cdn/x.ofd\", \"pdf\":\"https://cdn/x.pdf\",\n"
                + "  \"xmlurl\":\"https://cdn/x.xml\", \"jfdz\":\"https://etax/delivery\"\n"
                + "}"));
        assertEquals(InvoiceState.SUCCEEDED, event.getState());
        assertEquals("true", event.getAttrs().get("layoutReady"));
        assertEquals(3, event.getFiles().size());
        boolean hasXml = false;
        for (InvoiceFile file : event.getFiles()) {
            if (file.getType() == InvoiceFileType.XML) {
                hasXml = true;
                assertEquals("https://cdn/x.xml", file.getUrl());
                assertTrue(file.hasPayload());
                assertTrue(file.isLegalOriginal());
            }
        }
        assertTrue(hasXml, "qdbswj 必须给出 XML 法定原件地址");
        assertEquals("https://etax/delivery", event.getAttrs().get("jfdz"));
    }

    @Test
    void fpkj_statusMappings() {
        assertEquals(InvoiceState.FAILED,
                parse(status("fpkj", "2", "余额不足")).getState());
        assertEquals(InvoiceState.PROCESSING,
                parse(status("fpkj", "3", "")).getState());
        assertEquals(InvoiceState.CLOSED,
                parse(status("fpkj", "6", "")).getState());

        InvoiceCallbackEvent login = parse(status("fpkj", "1004", ""));
        assertEquals(InvoiceState.WAITING_AUTH, login.getState());
        assertEquals("LOGIN", login.getAttrs().get("authType"));

        InvoiceCallbackEvent face = parse(status("fpkj", "1003", ""));
        assertEquals(InvoiceState.WAITING_AUTH, face.getState());
        assertEquals("FACE", face.getAttrs().get("authType"));
    }

    @Test
    void fpkj_redResult_marksOriginalInvoiceNo() {
        InvoiceCallbackEvent event = parse(callback("{\n"
                + "  \"ywlxdm\":\"fpkj\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"qdfphm\":\"24312000000000000002\",\n"
                + "  \"yqdfphm\":\"24312000000000000001\", \"hjje\":\"-897.25\"\n"
                + "}"));
        assertEquals("24312000000000000001", event.getAttrs().get("originalInvoiceNo"));
        assertEquals("true", event.getAttrs().get("redInvoice"));
        assertNull(event.getState());
    }

    @Test
    void qdhzsq_redApplyStates() {
        InvoiceCallbackEvent ok = parse(callback("{\n"
                + "  \"ywlxdm\":\"qdhzsq\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"hzfpxxqrdbh\":\"34010524071001103631\", \"uuid\":\"u-1\"\n"
                + "}"));
        assertEquals(RedApplyState.WAITING_CONFIRM, ok.getRedState());
        assertEquals("34010524071001103631", ok.getRedConfirmNo());
        assertEquals("u-1", ok.getAttrs().get("redApplyUuid"));
        assertNull(ok.getState());

        InvoiceCallbackEvent fail = parse(callback("{\n"
                + "  \"ywlxdm\":\"qdhzsq\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"2\",\n"
                + "  \"ycyy\":\"原票状态不允许红冲\"\n"
                + "}"));
        assertEquals(RedApplyState.FAILED, fail.getRedState());
        assertEquals("原票状态不允许红冲", fail.getErrorMsg());
    }

    @Test
    void ewmrz_andCxnsrfx_registerAuthInfoWithoutStateChange() {
        InvoiceCallbackEvent qr = parse(callback("{\n"
                + "  \"ywlxdm\":\"ewmrz\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"rzid\":\"rz-1\", \"ewm\":\"data:image/png;base64,AAAA\"\n"
                + "}"));
        assertNull(qr.getState());
        assertEquals("rz-1", qr.getAttrs().get("rzid"));

        InvoiceCallbackEvent risk = parse(callback("{\n"
                + "  \"ywlxdm\":\"cxnsrfx\", \"ddlsh\":\"" + DDLSH + "\", \"status\":\"1\",\n"
                + "  \"sfsl\":\"N\", \"sxlb\":\"4\", \"yjjb\":\"01\"\n"
                + "}"));
        assertEquals("N", risk.getAttrs().get("sfsl"));
        assertEquals("4", risk.getAttrs().get("sxlb"));
    }

    @Test
    void qdbswj_failed_doesNotAdvanceSuccess() {
        InvoiceCallbackEvent event = parse(callback("{\n"
                + "  \"ywlxdm\":\"qdbswj\", \"ddlsh\":\"" + DDLSH + "\",\n"
                + "  \"status\":\"2\", \"ycyy\":\"版式生成失败\"\n"
                + "}"));
        assertNull(event.getState());
        assertEquals("版式生成失败", event.getErrorMsg());
    }

    @Test
    void reply_matchesPlatformContract() {
        InvoiceCallbackReply ok = BaiwangJsCallbackParser.reply(true, "SUCCESS");
        assertTrue(ok.isSuccess());
        Map<String, Object> body = JsonUtils.toMap(ok.getBody());
        assertEquals("0", String.valueOf(body.get("code")));
        assertEquals("成功", body.get("message"));
        assertEquals("application/json; charset=utf-8", ok.getContentType());

        InvoiceCallbackReply bad = BaiwangJsCallbackParser.reply(false, "验签失败");
        assertTrue(!bad.isSuccess());
        assertEquals("1", String.valueOf(JsonUtils.toMap(bad.getBody()).get("code")));
        assertNotNull(bad.getBody(), "失败也应返回 JSON 应答体，避免平台收不到应答");
    }

    @Test
    void invalidBase64_throwsParseFailed() {
        assertThrows(GatewayException.class,
                () -> BaiwangJsCallbackParser.parse(rawWithBody("%%%bad%%%"), config(null)));
    }

    @Test
    void optionalSign_whenConfigured_isVerified() {
        String json = "{\"ywlxdm\":\"fpkj\",\"ddlsh\":\"" + DDLSH + "\",\"status\":\"1\"}";
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("callbackSignKey", "sign-key");
        String sign = io.github.lianghang1.conflux.common.util.SignUtil
                .hmacSha256Hex(json, "sign-key");
        InvoiceCallbackRequest raw = rawWithBody(base64(json));
        raw.getQueryParams().put("sign", sign);
        assertEquals(DDLSH, BaiwangJsCallbackParser.parse(raw, config(attrs)).getBizOrderNo());

        raw.getQueryParams().put("sign", "wrong");
        GatewayException ex = assertThrows(GatewayException.class,
                () -> BaiwangJsCallbackParser.parse(raw, config(attrs)));
        assertTrue(ex.getMessage().contains("验签失败"));
    }

    // ---- 夹具 ----

    private InvoiceCallbackEvent parse(InvoiceCallbackRequest raw) {
        return BaiwangJsCallbackParser.parse(raw, config(null));
    }

    private InvoiceCallbackRequest callback(String json) {
        return rawWithBody(base64(json));
    }

    private InvoiceCallbackRequest rawWithBody(String encodedBody) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put(BaiwangJsSpec.ACCOUNT_KEY_PARAM, "gw-key#91310000TEST000001");
        return new InvoiceCallbackRequest()
                .setChannelCode(BaiwangJsSpec.CODE)
                .setQueryParams(query)
                .setBody(encodedBody);
    }

    private InvoiceCallbackRequest status(String ywlxdm, String status, String ycyy) {
        return callback("{\"ywlxdm\":\"" + ywlxdm + "\",\"ddlsh\":\"" + DDLSH
                + "\",\"status\":\"" + status + "\",\"ycyy\":\"" + ycyy + "\"}");
    }

    private ChannelConfig config(Map<String, String> attrs) {
        return new ChannelConfig()
                .setAppId("demo").setChannelCode(BaiwangJsSpec.CODE)
                .setAccountKey("gw-key#91310000TEST000001")
                .setAttrs(attrs);
    }

    private String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
