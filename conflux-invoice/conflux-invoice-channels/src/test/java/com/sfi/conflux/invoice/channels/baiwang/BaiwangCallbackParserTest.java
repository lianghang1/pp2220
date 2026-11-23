package com.sfi.conflux.invoice.channels.baiwang;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.invoice.api.InvoiceCallbackEvent;
import com.sfi.conflux.invoice.api.InvoiceCallbackReply;
import com.sfi.conflux.invoice.api.InvoiceCallbackRequest;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channels.baiwang.support.BaiwangCallbackParser;
import com.sfi.conflux.invoice.model.InvoiceFileType;
import com.sfi.conflux.invoice.model.InvoiceState;
import com.sfi.conflux.invoice.model.RedApplyState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 百望云回调解析回归：method/type 分流、状态归一、文件内联、可选验签。
 */
class BaiwangCallbackParserTest {

    @Test
    void parseInvoiceSuccessCallback() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("serialNo", "BW-S1");
        data.put("orderNo", "ORDER-1");
        data.put("invoiceNo", "24652000000012345678");
        data.put("invoiceCode", "033002600111");
        data.put("invoiceDate", "2026-11-17 10:30:00");
        data.put("invoiceTotalPriceTax", "113.00");
        data.put("invoiceTotalTax", "13.00");
        data.put("xmlUrl", "https://example.com/a.xml");
        data.put("pdfUrl", "https://example.com/a.pdf");

        InvoiceCallbackEvent event = BaiwangCallbackParser.parse(
                callback(BaiwangSpec.METHOD_CALLBACK_INVOICE, BaiwangSpec.CALLBACK_SUCCESS, data), null);

        assertThat(event.getState()).isEqualTo(InvoiceState.SUCCEEDED);
        assertThat(event.getInvoiceNo()).isEqualTo("24652000000012345678");
        assertThat(event.getChannelOrderNo()).isEqualTo("BW-S1");
        assertThat(event.getAmount()).isEqualByComparingTo("113.00");
        assertThat(event.getFiles()).extracting(f -> f.getType())
                .contains(InvoiceFileType.XML, InvoiceFileType.PDF);
    }

    @Test
    void parseInvoiceFailedCallback() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("serialNo", "BW-S2");
        InvoiceCallbackEvent event = BaiwangCallbackParser.parse(
                callback(BaiwangSpec.METHOD_CALLBACK_INVOICE, BaiwangSpec.CALLBACK_FAILED, data), null);
        assertThat(event.getState()).isEqualTo(InvoiceState.FAILED);
    }

    @Test
    void parseUnknownCallbackTypeDoesNotMigrateState() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("serialNo", "BW-S3");
        InvoiceCallbackEvent event = BaiwangCallbackParser.parse(
                callback(BaiwangSpec.METHOD_CALLBACK_INVOICE, "99", data), null);
        assertThat(event.getState()).isNull();
    }

    @Test
    void parseRedConfirmCallback() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("redInfoNo", "RED-1");
        data.put("redConfirmStatus", BaiwangSpec.RED_CONFIRMED);
        InvoiceCallbackEvent event = BaiwangCallbackParser.parse(
                callback(BaiwangSpec.METHOD_CALLBACK_RED_CONFIRM, null, data), null);
        assertThat(event.getRedState()).isEqualTo(RedApplyState.CONFIRMED);
        assertThat(event.getRedConfirmNo()).isEqualTo("RED-1");
        assertThat(event.getState()).isNull();
    }

    @Test
    void signVerificationRejectsBadSignature() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("serialNo", "BW-S4");
        InvoiceCallbackRequest raw = callback(BaiwangSpec.METHOD_CALLBACK_INVOICE,
                BaiwangSpec.CALLBACK_SUCCESS, data, "deadbeef");
        ChannelConfig config = new ChannelConfig();
        config.setAttr(java.util.Collections.singletonMap("callbackSignKey", "secret"));
        assertThatThrownBy(() -> BaiwangCallbackParser.parse(raw, config))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void replyFormat() {
        InvoiceCallbackReply ok = BaiwangCallbackParser.reply(true, null);
        assertThat(ok.isSuccess()).isTrue();
        assertThat(ok.getBody()).contains("\"code\":\"0\"");
        InvoiceCallbackReply fail = BaiwangCallbackParser.reply(false, "x");
        assertThat(fail.getBody()).contains("\"code\":\"1\"");
    }

    /** 构造百望回调请求：data 为 JSON 字符串（与平台一致）。 */
    private static InvoiceCallbackRequest callback(String method, String type, Map<String, Object> data) {
        return callback(method, type, data, null);
    }

    private static InvoiceCallbackRequest callback(String method, String type,
                                                   Map<String, Object> data, String sign) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("method", method);
        root.put("type", type);
        root.put("taxNo", "338888888888SMB");
        root.put("version", "1.0.6");
        root.put("data", JsonUtils.toJson(data));
        if (sign != null) {
            root.put("sign", sign);
        }
        InvoiceCallbackRequest req = new InvoiceCallbackRequest();
        req.setBody(JsonUtils.toJson(root));
        return req;
    }
}
