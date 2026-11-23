package com.sfi.conflux.invoice.channels.nuonuo.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.invoice.api.InvoiceCallbackEvent;
import com.sfi.conflux.invoice.api.InvoiceCallbackReply;
import com.sfi.conflux.invoice.api.InvoiceCallbackRequest;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channels.nuonuo.NuonuoSpec;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;
import com.sfi.conflux.invoice.model.InvoiceFile;
import com.sfi.conflux.invoice.model.InvoiceFileType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诺诺开票结果回调解析（JSON body）。
 *
 * <p>回调报文（开票异步通知）：{@code action/sale_tax_num/order_no/invoice_down_url/
 * invoice_no/status/message}。{@code action=nuonuo_billing_new_notice} 表示开票结果；
 * status：1 完成 / 2 失败 / 3 签章失败。PDF 下载地址在 {@code invoice_down_url}。</p>
 *
 * <p><b>验签</b>：公开文档与 SDK 未提供回调验签规则，本实现不臆造验签；
 * 状态以渠道通知为准归一，资金/金额敏感场景由使用方按业务单号自行查单复核。</p>
 *
 * @author lianghang
 */
public final class NuonuoCallbackParser {

    private NuonuoCallbackParser() {
    }

    public static InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        if (raw == null || StringUtils.isBlank(raw.getBody())) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "诺诺回调报文为空");
        }
        JsonNode root;
        try {
            root = JsonUtils.readTree(raw.getBody());
        } catch (RuntimeException e) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "诺诺回调不是合法 JSON: " + e.getMessage(), e);
        }
        String action = text(root, "action");
        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setRawPayload(raw.getBody());

        if (NuonuoSpec.NOTICE_ACTION.equals(action)
                || StringUtils.isBlank(action)) {
            fillInvoice(event, root);
        } else {
            // 未知 action：只登记流水，不迁移状态
            event.setBizOrderNo(text(root, "order_no"))
                    .setAttr(attrOf(action, text(root, "sale_tax_num")));
        }
        return event;
    }

    private static void fillInvoice(InvoiceCallbackEvent event, JsonNode root) {
        event.setBizOrderNo(text(root, "order_no"))
                .setChannelOrderNo(text(root, "serial_no", "serialNo"))
                .setInvoiceNo(text(root, "invoice_no"))
                .setState(NuonuoCodec.stateOfNotice(text(root, "status")));
        String msg = text(root, "message");
        if (StringUtils.isNotBlank(msg)
                && event.getState() == com.sfi.conflux.invoice.model.InvoiceState.FAILED) {
            event.setErrorMsg(msg);
        }
        String pdf = text(root, "invoice_down_url");
        if (StringUtils.isNotBlank(pdf)) {
            Map<String, String> attr = new LinkedHashMap<String, String>();
            attr.put("pdfUrl", pdf);
            event.setAttr(attr);
            java.util.List<InvoiceFile> files = new java.util.ArrayList<InvoiceFile>();
            files.add(new InvoiceFile().setType(InvoiceFileType.PDF).setUrl(pdf));
            event.setFiles(files);
        }
    }

    /** 应答：文档要求成功返回字符串 success，其他内容触发重推。 */
    public static InvoiceCallbackReply reply(boolean success, String message) {
        return InvoiceCallbackReply.ok(NuonuoSpec.REPLY_SUCCESS);
    }

    private static Map<String, String> attrOf(String action, String taxNo) {
        Map<String, String> attr = new LinkedHashMap<String, String>();
        if (StringUtils.isNotBlank(action)) {
            attr.put("action", action);
        }
        if (StringUtils.isNotBlank(taxNo)) {
            attr.put("sale_tax_num", taxNo);
        }
        return attr;
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
}
