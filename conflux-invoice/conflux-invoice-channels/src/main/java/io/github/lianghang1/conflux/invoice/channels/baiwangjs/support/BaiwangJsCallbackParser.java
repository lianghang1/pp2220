package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 百旺金穗云异步回调解析：Base64 明文 → 按 {@code ywlxdm} 分流 → 框架语义事件。
 *
 * <p><b>平台语义与框架语义的关键差异</b>：平台把「票号/结果」（{@code fpkj}）与
 * 「版式文件地址」（{@code qdbswj}）拆成两次独立回调；而框架要求
 * {@code SUCCEEDED} 前必须归档 XML 法定原件。因此：</p>
 * <ul>
 *   <li>{@code fpkj status=1} → <b>只补充票面字段</b>（state 为空，不迁移状态）；</li>
 *   <li>{@code qdbswj status=1} → 携带 XML/OFD/PDF 地址并置 {@code SUCCEEDED}；</li>
 *   <li>两次回调各自产出归一事件，使用方按业务单号把票号与法定原件合并到自己的单据。</li>
 * </ul>
 *
 * <p><b>验签</b>：平台公开文档未定义回调签名头/参数，故不伪造验签规则；
 * 回调验签后归一为框架事件，
 * 生产环境应通过网络层白名单 / 网关侧校验保障。若宿主另行约定了签名，可通过
 * {@code attr.callbackSignKey} + 查询参数 {@code sign} 启用 HMAC 校验。</p>
 *
 * @author lianghang
 */
@Slf4j
public final class BaiwangJsCallbackParser {

    private static final DateTimeFormatter KPRQ = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BaiwangJsCallbackParser() {
    }

    public static InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        String json = BaiwangJsCodec.decodeOrPlain(raw == null ? null : raw.getBody());
        assertCallbackSign(raw, config, json);
        JsonNode root;
        try {
            root = JsonUtils.readTree(json);
        } catch (IllegalArgumentException e) {
            // JsonUtils 统一抛 JDK 异常，回调解析失败需归一为框架异常
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百旺金穗云回调不是合法 JSON 对象: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED,
                    "百旺金穗云回调不是合法 JSON 对象");
        }

        String ywlxdm = str(root, "ywlxdm");
        String ddlsh = str(root, "ddlsh");
        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setChannelOrderNo(ddlsh)
                .setBizOrderNo(ddlsh)
                .setRawPayload(json);
        Map<String, String> attr = new LinkedHashMap<String, String>();
        if (StringUtils.isNotBlank(ywlxdm)) {
            attr.put("ywlxdm", ywlxdm);
        }

        if (BaiwangJsSpec.YW_QDBSWJ.equalsIgnoreCase(ywlxdm)) {
            parseLayout(root, event, attr);
        } else if (BaiwangJsSpec.YW_QDHZSQ.equalsIgnoreCase(ywlxdm)) {
            parseRedApply(root, event, attr);
        } else if (BaiwangJsSpec.YW_EWMRZ.equalsIgnoreCase(ywlxdm)
                || BaiwangJsSpec.YW_CXNSRFX.equalsIgnoreCase(ywlxdm)) {
            parseAuth(root, event, attr);
        } else if (BaiwangJsSpec.YW_HZCX.equalsIgnoreCase(ywlxdm)) {
            // 3.3.17 可冲销明细查询的异步结果：只携带金额/明细摘要，不置开票状态，
            // 否则会被误当成 fpkj 开票结果回调。
            parseRedAvailable(root, event, attr);
        } else {
            // fpkj（蓝字开票结果 / 红字开具结果）及未知类型统一按开票结果处理
            parseIssue(root, event, attr);
        }
        event.setAttr(attr);
        return event;
    }

    /** hzcx：可冲销明细查询结果，登记到 attr 供排障/后续对账，不改状态。 */
    private static void parseRedAvailable(JsonNode root, InvoiceCallbackEvent event,
                                          Map<String, String> attr) {
        put(attr, "hzcxje", str(root, "hzcxje"));
        put(attr, "hzcxse", str(root, "hzcxse"));
        put(attr, "jshj", str(root, "jshj"));
        put(attr, "channelResult", "HZCX:" + nullTo(str(root, "status"), ""));
        event.setErrorMsg(str(root, "ycyy"));
    }

    /** fpkj：票号、日期、金额、状态。成功不直接置 SUCCEEDED，等待 qdbswj 补法定原件。 */
    private static void parseIssue(JsonNode root, InvoiceCallbackEvent event,
                                   Map<String, String> attr) {
        String status = str(root, "status");
        String originalNo = str(root, "yqdfphm");
        if (StringUtils.isNotBlank(originalNo)) {
            attr.put("originalInvoiceNo", originalNo);
            attr.put("redInvoice", "true");
        }

        event.setInvoiceNo(firstNonBlank(str(root, "qdfphm"), str(root, "fphm")));
        event.setInvoiceCode(str(root, "fpdm"));
        event.setInvoiceDate(parseDate(str(root, "kprq")));
        event.setAmount(dec(root, "jshj"));
        event.setTaxAmount(dec(root, "hjse"));
        event.setErrorCode(str(root, "code"));
        event.setErrorMsg(str(root, "ycyy", "message"));

        if (BaiwangJsSpec.ST_SUCCESS.equals(status)) {
            // 只补票面字段，状态由 qdbswj（版式回调）推进到 SUCCEEDED
            event.setState(null);
            attr.put("faceFieldsOnly", "true");
            attr.put("channelResult", "SUCCESS");
        } else if (BaiwangJsSpec.ST_FAILED.equals(status)) {
            event.setState(InvoiceState.FAILED);
            event.setErrorCode(firstNonBlank(str(root, "code"), "FPKJ_FAILED"));
        } else if (BaiwangJsSpec.ST_NO_PDF.equals(status)) {
            event.setState(InvoiceState.PROCESSING);
            attr.put("channelResult", "NO_PDF");
        } else if (BaiwangJsSpec.ST_DELETED.equals(status)) {
            event.setState(InvoiceState.CLOSED);
        } else if (BaiwangJsSpec.ST_NEED_LOGIN.equals(status)) {
            waitingAuth(event, attr, "LOGIN", "需要登录电子税务局后继续开票");
        } else if (BaiwangJsSpec.ST_NEED_AUTH.equals(status)) {
            waitingAuth(event, attr, "FACE", "需要完成实人认证（扫脸）后继续开票");
        } else {
            event.setState(InvoiceState.PROCESSING);
            attr.put("channelResult", "UNKNOWN_STATUS:" + nullTo(status, ""));
        }
    }

    /** qdbswj：版式文件地址（XML 是法定原件）。status=1 时推进 SUCCEEDED。 */
    private static void parseLayout(JsonNode root, InvoiceCallbackEvent event,
                                    Map<String, String> attr) {
        String status = str(root, "status");
        event.setErrorMsg(str(root, "ycyy"));
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        addUrl(files, root, InvoiceFileType.XML, "xmlurl", "xml");
        addUrl(files, root, InvoiceFileType.OFD, "ofdurl");
        addUrl(files, root, InvoiceFileType.PDF, "pdf");
        event.setFiles(files);

        String delivery = str(root, "jfdz");
        if (StringUtils.isNotBlank(delivery)) {
            attr.put("jfdz", delivery);
        }
        if (BaiwangJsSpec.ST_SUCCESS.equals(status) || StringUtils.isBlank(status)) {
            // 版式就绪：携带法定原件地址，由编排层归档并置成功
            event.setState(InvoiceState.SUCCEEDED);
            attr.put("layoutReady", "true");
        } else {
            // 版式生成失败/需要认证：不推进成功，仅登记
            event.setState(null);
            attr.put("layoutStatus", status);
            if (BaiwangJsSpec.ST_NEED_LOGIN.equals(status)) {
                waitingAuth(event, attr, "LOGIN", "需要登录电子税务局后重新生成版式文件");
            } else if (BaiwangJsSpec.ST_NEED_AUTH.equals(status)) {
                waitingAuth(event, attr, "FACE", "需要实人认证后重新生成版式文件");
            }
        }
    }

    /** qdhzsq：红字申请单结果。 */
    private static void parseRedApply(JsonNode root, InvoiceCallbackEvent event,
                                      Map<String, String> attr) {
        String status = str(root, "status");
        event.setRedConfirmNo(str(root, "hzfpxxqrdbh"));
        event.setErrorMsg(str(root, "ycyy"));
        attr.put("redApplyUuid", nullTo(str(root, "uuid"), ""));
        if (BaiwangJsSpec.ST_SUCCESS.equals(status)) {
            event.setRedState(RedApplyState.WAITING_CONFIRM);
        } else if (BaiwangJsSpec.ST_FAILED.equals(status)) {
            event.setRedState(RedApplyState.FAILED);
            event.setErrorCode(firstNonBlank(str(root, "code"), "RED_APPLY_FAILED"));
        } else if (BaiwangJsSpec.ST_NEED_LOGIN.equals(status)
                || BaiwangJsSpec.ST_NEED_AUTH.equals(status)) {
            event.setRedState(RedApplyState.WAITING_CONFIRM);
            attr.put("authRequired", status);
        } else {
            event.setRedState(RedApplyState.WAITING_CONFIRM);
        }
    }

    /** ewmrz / cxnsrfx：授权与认证信息，登记到 attr，不直接改单据状态。 */
    private static void parseAuth(JsonNode root, InvoiceCallbackEvent event,
                                  Map<String, String> attr) {
        put(attr, "rzid", str(root, "rzid"));
        put(attr, "ewm", str(root, "ewm"));
        put(attr, "sfsl", str(root, "sfsl"));
        put(attr, "sxlb", str(root, "sxlb"));
        put(attr, "yjjb", str(root, "yjjb"));
        event.setErrorMsg(str(root, "ycyy"));
        String status = str(root, "status");
        if (BaiwangJsSpec.ST_FAILED.equals(String.valueOf(status))) {
            event.setErrorMsg(firstNonBlank(str(root, "ycyy"), "认证失败"));
        }
    }

    private static void waitingAuth(InvoiceCallbackEvent event, Map<String, String> attr,
                                    String authType, String text) {
        event.setState(InvoiceState.WAITING_AUTH);
        attr.put("authType", authType);
        attr.put("challengeText", text);
    }

    /** 回调应答：平台要求 {@code {"code":"0","message":"成功"}}，失败也需返回 JSON。 */
    public static InvoiceCallbackReply reply(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", success ? "0" : "1");
        body.put("message", success ? "成功" : nullTo(message, "处理失败"));
        return InvoiceCallbackReply.ok(JsonUtils.toJson(body)).setSuccess(success)
                .setMessage(message);
    }

    /** 可选验签：仅当宿主配置了 callbackSignKey 且回调带 sign 时启用，不伪造平台规则。 */
    private static void assertCallbackSign(InvoiceCallbackRequest raw, ChannelConfig config,
                                           String json) {
        if (raw == null || raw.getQueryParams() == null || config == null
                || config.getAttr() == null) {
            return;
        }
        String key = config.getAttr().get("callbackSignKey");
        String sign = raw.getQueryParams().get("sign");
        if (StringUtils.isBlank(key) || StringUtils.isBlank(sign)) {
            return;
        }
        String expected = io.github.lianghang1.conflux.common.util.SignUtil
                .hmacSha256Hex(json, key);
        if (!io.github.lianghang1.conflux.common.util.SignUtil.constantTimeEquals(expected, sign)) {
            throw new GatewayException(InvoiceErrorCode.SIGN_INVALID,
                    "百旺金穗云回调验签失败");
        }
    }

    private static void addUrl(List<InvoiceFile> files, JsonNode root, InvoiceFileType type,
                               String... names) {
        String url = str(root, names);
        if (StringUtils.isBlank(url)) {
            return;
        }
        files.add(new InvoiceFile().setType(type).setUrl(url));
    }

    private static LocalDate parseDate(String kprq) {
        if (StringUtils.isBlank(kprq) || kprq.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(kprq.substring(0, 8), KPRQ);
        } catch (Exception e) {
            log.debug("百旺金穗云回调开票日期解析失败，按 null 处理，原值: {}", kprq, e);
            return null;
        }
    }

    private static String str(JsonNode root, String... names) {
        return BaiwangJsCodec.text(root, names);
    }

    private static BigDecimal dec(JsonNode root, String... names) {
        return BaiwangJsCodec.decimalValue(root, names);
    }

    private static void put(Map<String, String> attr, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            attr.put(key, value);
        }
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
}
