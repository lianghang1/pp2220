package io.github.lianghang1.conflux.invoice.channels.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.http.HttpTransportException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.channel.AuthSessionHandler;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceCallbackHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.AuthType;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用 REST 渠道适配器：所有服务商渠道共用一套「组装 JSON → common HTTP 传输层 →
 * 解析原始响应 → 归一化为框架语义」的流程，渠道差异集中在 {@link RestChannelSpec}。
 *
 * <p><b>传输层边界</b>：只依赖 {@link HttpTransport}（OkHttp 已被封在 common），
 * 非 2xx 由本类按渠道协议判定，IO 异常中立化后翻译成
 * {@code CHANNEL_CALL_FAILED}，不把 OkHttp 泄漏到渠道包。</p>
 *
 * <p><b>能力按需暴露</b>：路径未配置的能力返回 null，注册表会明确报
 * 「渠道不支持该能力」，而不是伪造成功。签名默认是通用 HMAC-SHA256 占位，
 * 各平台签名规则不同，需按文档覆写 {@link #buildHeaders}。</p>
 *
 * <p>实现无状态、线程安全，全部运行态数据从 {@link InvoiceContext} 取得。</p>
 *
 * @author lianghang
 */
@Slf4j
public abstract class AbstractRestInvoiceChannel implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceFileFetcher, InvoiceRedHandler,
        AuthSessionHandler, InvoiceCallbackHandler {

    protected final RestChannelSpec spec;

    protected AbstractRestInvoiceChannel(RestChannelSpec spec) {
        this.spec = spec;
        if (!spec.isDocumentVerified()) {
            log.warn("渠道[{}]的接口路径与字段映射尚未按公开文档核对，相关能力默认不暴露: {}",
                    spec.getChannelCode(), spec.getDocNote());
        }
    }

    // ---- SPI 元信息 ----

    @Override
    public String channelCode() {
        return spec.getChannelCode();
    }

    @Override
    public ChannelTier channelTier() {
        return spec.getTier();
    }

    @Override
    public Set<InvoiceType> supportedTypes() {
        return spec.getSupportedTypes();
    }

    @Override
    public InvoiceChannelParams parseParams(Map<String, String> properties) {
        Map<String, String> props = properties == null
                ? Collections.<String, String>emptyMap() : properties;
        RestInvoiceParams params = new RestInvoiceParams()
                .setChannelAppId(firstNonBlank(props.get("appKey"), props.get("channelAppId")))
                .setSellerTaxNo(firstNonBlank(props.get("sellerTaxNo"), props.get("nsrsbh")))
                .setAppKey(props.get("appKey"))
                .setAppSecret(props.get("appSecret"))
                .setBaseUrl(firstNonBlank(props.get("baseUrl"), spec.getBaseUrl()))
                .setSandbox(Boolean.parseBoolean(props.get("sandbox")))
                .setTimeoutMillis(parseLong(props.get("timeoutMillis"), 30000L))
                .setAttrs(new LinkedHashMap<String, String>(props));
        Map<String, String> typeCodes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith("fplxdm.")) {
                typeCodes.put(entry.getKey().substring("fplxdm.".length()), entry.getValue());
            }
        }
        params.setInvoiceTypeCodes(typeCodes);
        return params;
    }

    // ---- 能力暴露 ----

    @Override
    public InvoiceIssuer issuer() {
        return available(spec.getIssuePath()) ? this : null;
    }

    @Override
    public InvoiceQuerier querier() {
        return available(spec.getQueryPath()) ? this : null;
    }

    @Override
    public InvoiceFileFetcher fileFetcher() {
        return available(spec.getFilePath()) ? this : null;
    }

    @Override
    public InvoiceRedHandler redHandler() {
        return spec.hasRed() || spec.isConfigDriven() ? this : null;
    }

    @Override
    public AuthSessionHandler authHandler() {
        return spec.hasAuth() || spec.isConfigDriven() ? this : null;
    }

    /**
     * 已核对渠道按规格中确实存在的路径暴露能力；未核对渠道作为「可配置骨架」，
     * 允许使用方按平台文档在 attrs 中配置路径后启用，因此能力先暴露、
     * 真正调用时在 {@link #call} 里校验路径是否已配置。
     */
    private boolean available(String specPath) {
        return (specPath != null && specPath.length() > 0) || spec.isConfigDriven();
    }

    @Override
    public InvoiceCallbackHandler callbackHandler() {
        return this;
    }

    // ---- 蓝字开票 ----

    @Override
    public InvoiceResult issue(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        RestInvoiceParams params = params(ctx);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "action", spec.getActionCode());
        put(body, "async", invoice.isAsyncAllowed());
        put(body, "nsrsbh", invoice.getSellerTaxNo());
        put(body, "ly_ddbh", invoice.getBizOrderNo());
        put(body, "jshj", invoice.getAmount());
        put(body, "hjse", invoice.getTaxAmount());
        put(body, "wjgs", "xml");
        put(body, "fplxdm", params.invoiceTypeCode(invoice.getInvoiceType()));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        put(data, "xsf_yhmc", invoice.getSellerName());
        put(data, "gmf_mc", invoice.getBuyerTitle());
        put(data, "gmf_nsrsbh", invoice.getBuyerTaxNo());
        if (!data.isEmpty()) {
            body.put("data", data);
        }
        if (invoice.getItems() != null) {
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (InvoiceItem item : invoice.getItems()) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<String, Object>();
                put(line, "xmmc", item.getItemName());
                put(line, "ggxh", item.getSpecification());
                put(line, "je", item.getAmount());
                put(line, "sl", item.getTaxRate());
                put(line, "se", item.getTaxAmount());
                put(line, "spbm", item.getTaxClassificationCode());
                items.add(line);
            }
            body.put("xmmx", items);
        }
        View view = call(ctx, "issuePath", spec.getIssuePath(), body);
        return toInvoiceResult(view);
    }

    // ---- 查单 ----

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "nsrsbh", invoice.getSellerTaxNo());
        put(body, "ly_ddbh", invoice.getBizOrderNo());
        put(body, "reqId", invoice.getChannelOrderNo());
        put(body, "fphm", invoice.getInvoiceNo());
        View view = call(ctx, "queryPath", spec.getQueryPath(), body);
        return toInvoiceResult(view);
    }

    // ---- 版式文件下载 ----

    @Override
    public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
        Invoice invoice = ctx.requireInvoice();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "reqId", invoice.getChannelOrderNo());
        put(body, "fphm", invoice.getInvoiceNo());
        put(body, "wjgs", "xml");
        View view = call(ctx, "filePath", spec.getFilePath(), body);

        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (StringUtils.isNotBlank(view.xml)) {
            files.add(new InvoiceFile().setType(InvoiceFileType.XML).setContent(view.xml));
        }
        String ofdUrl = text(view.data, "ofdUrl", "ofd", "ofdUrlPath");
        String pdfUrl = text(view.data, "pdfUrl", "pdf", "pdfUrlPath");
        if (StringUtils.isNotBlank(ofdUrl) && contains(types, InvoiceFileType.OFD)) {
            files.add(new InvoiceFile().setType(InvoiceFileType.OFD).setUrl(ofdUrl));
        }
        if (StringUtils.isNotBlank(pdfUrl) && contains(types, InvoiceFileType.PDF)) {
            files.add(new InvoiceFile().setType(InvoiceFileType.PDF).setUrl(pdfUrl));
        }
        return files;
    }

    // ---- 红字两段 ----

    @Override
    public RedApplyResult apply(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "ly_ddbh", apply == null ? null : apply.getBizRedNo());
        put(body, "yfphm", apply == null ? null : apply.getOriginalInvoiceNo());
        put(body, "yy", apply == null ? null : apply.getReason());
        put(body, "jshj", apply == null ? null : apply.getAmount());
        View view = call(ctx, "redApplyPath", spec.getRedApplyPath(), body);
        if (!view.success) {
            return RedApplyResult.failed(view.code, view.message);
        }
        String confirmNo = text(view.data, "redConfirmNo", "hzqrdh", "confirmNo");
        LocalDateTime deadline = dateTime(view.data, "confirmDeadline", "deadline");
        return RedApplyResult.waiting(firstNonBlank(view.reqId, confirmNo),
                deadline == null ? LocalDateTime.now().plusHours(72L) : deadline)
                .setRedConfirmNo(confirmNo);
    }

    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "ly_ddbh", apply == null ? null : apply.getBizRedNo());
        put(body, "reqId", apply == null ? null : apply.getChannelOrderNo());
        put(body, "hzqrdh", apply == null ? null : apply.getRedConfirmNo());
        View view = call(ctx, "redQueryPath", spec.getRedQueryPath(), body);
        if (!view.success) {
            return RedApplyResult.failed(view.code, view.message);
        }
        String stateText = text(view.data, "state", "status", "qrzt");
        RedApplyState state = redState(stateText);
        RedApplyResult result = new RedApplyResult().setState(state)
                .setRedConfirmNo(text(view.data, "redConfirmNo", "hzqrdh"));
        LocalDateTime deadline = dateTime(view.data, "confirmDeadline", "deadline");
        if (deadline != null) {
            result.setConfirmDeadline(deadline);
        }
        return result;
    }

    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        RedApply apply = ctx.getRedApply();
        put(body, "ly_ddbh", apply == null ? null : apply.getBizRedNo());
        put(body, "hzqrdh", apply == null ? null : apply.getRedConfirmNo());
        put(body, "jshj", apply == null ? null : apply.getAmount());
        View view = call(ctx, "redIssuePath", spec.getRedIssuePath(), body);
        return toInvoiceResult(view);
    }

    @Override
    public QuickRedOutcome quickRed(InvoiceContext ctx) {
        RedApplyResult outcome = queryRed(ctx);
        if (outcome != null && outcome.getState() == RedApplyState.CONFIRMED) {
            return QuickRedOutcome.issued(issueRed(ctx), outcome);
        }
        return QuickRedOutcome.waiting(outcome == null
                ? RedApplyResult.waiting("RED", LocalDateTime.now().plusHours(72L))
                : outcome);
    }

    // ---- 授权会话 ----

    @Override
    public AuthResult start(InvoiceContext ctx) {
        AuthSession session = ctx.getAuthSession();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "invoiceId", session == null ? null : session.getInvoiceId());
        put(body, "nsrsbh", ctx.requireInvoice().getSellerTaxNo());
        View view = call(ctx, "authStartPath", spec.getAuthStartPath(), body);
        AuthChallenge challenge = new AuthChallenge()
                .setAuthType(AuthType.QR)
                .setChallengeUrl(text(view.data, "qrUrl", "authUrl", "url"))
                .setChallengeText(text(view.data, "tip", "message"))
                .setExpireTime(LocalDateTime.now().plusMinutes(10L));
        return AuthResult.pending(challenge);
    }

    @Override
    public AuthResult submit(InvoiceContext ctx, AuthSubmission submission) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "sessionId", submission.getSessionId());
        put(body, "captcha", submission.getCaptcha());
        put(body, "token", submission.getToken());
        View view = call(ctx, "authSubmitPath", spec.getAuthSubmitPath(), body);
        if (!view.success) {
            return AuthResult.rejected(view.code, view.message);
        }
        return AuthResult.verified(InvoiceState.SUBMITTING);
    }

    // ---- 回调 ----

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        String body = raw.getBody() == null ? "" : raw.getBody();
        RestInvoiceParams params = config.getParams() instanceof RestInvoiceParams
                ? (RestInvoiceParams) config.getParams() : null;
        String sign = raw.getQueryParams() == null ? null : raw.getQueryParams().get("sign");
        if (sign != null && params != null && StringUtils.isNotBlank(params.getAppSecret())) {
            String expected = SignUtil.hmacSha256Hex(body, params.getAppSecret());
            if (!expected.equalsIgnoreCase(sign)) {
                throw new GatewayException(InvoiceErrorCode.SIGN_INVALID, "发票回调验签失败");
            }
        }
        JsonNode root = JsonUtils.readTree(body);
        if (root == null) {
            throw new GatewayException(InvoiceErrorCode.CALLBACK_PARSE_FAILED, "发票回调报文不是合法 JSON");
        }
        InvoiceCallbackEvent event = new InvoiceCallbackEvent()
                .setAccountKey(raw.getQueryParams() == null ? null
                        : raw.getQueryParams().get("accountKey"))
                .setInvoiceId(text(root, "invoiceId"))
                .setRedApplyId(text(root, "redApplyId"))
                .setChannelOrderNo(text(root, "channelOrderNo", "reqId", "serialNo"))
                .setBizOrderNo(text(root, "bizOrderNo", "ly_ddbh"))
                .setInvoiceNo(text(root, "invoiceNo", "fphm"))
                .setInvoiceCode(text(root, "invoiceCode", "fpdm"))
                .setErrorCode(text(root, "errorCode", "code"))
                .setErrorMsg(text(root, "errorMsg", "message"))
                .setRawPayload(body);
        String stateText = text(root, "state", "status");
        if (StringUtils.isNotBlank(stateText)) {
            event.setState(parseInvoiceState(stateText));
        }
        String redStateText = text(root, "redState", "redStatus");
        if (StringUtils.isNotBlank(redStateText)) {
            event.setRedState(redState(redStateText));
        }
        String xml = text(root.path("data"), "xml", "xmlContent");
        if (StringUtils.isNotBlank(xml)) {
            event.setFiles(Collections.singletonList(
                    new InvoiceFile().setType(InvoiceFileType.XML).setContent(xml)));
        }
        return event;
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", success ? "SUCCESS" : "FAIL");
        body.put("message", message);
        return success ? InvoiceCallbackReply.ok(JsonUtils.toJson(body))
                : InvoiceCallbackReply.fail(message);
    }

    // ==================== 通用协议处理 ====================

    /** 组装请求头；各平台签名规则不同，按文档覆写本方法。 */
    protected Map<String, String> buildHeaders(RestInvoiceParams params, String jsonBody) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", HttpTransport.JSON);
        if (StringUtils.isNotBlank(params.getAppKey())) {
            headers.put("appKey", params.getAppKey());
        }
        long ts = System.currentTimeMillis();
        headers.put("timestamp", String.valueOf(ts));
        if (StringUtils.isNotBlank(params.getAppSecret())) {
            headers.put("sign", SignUtil.hmacSha256Hex(jsonBody + ts, params.getAppSecret()));
        }
        return headers;
    }

    /** 执行一次 REST 调用并做「HTTP 层 + 业务码层」两级判定。 */
    protected View call(InvoiceContext ctx, String pathKey, String specPath, Map<String, Object> body) {
        RestInvoiceParams params = params(ctx);
        String path = params.path(pathKey, specPath);
        if (path == null || path.length() == 0) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "渠道[{}]未配置 {} 接口路径，能力不可用；请在 conflux.invoice.apps.*.channels.*.attrs 中补齐",
                    spec.getChannelCode(), pathKey);
        }
        String json = JsonUtils.toJson(body);
        String url = params.url(path);
        try {
            HttpResponse response = HttpTransport.postText(url, buildHeaders(params, json),
                    HttpTransport.JSON, json, params.getTimeoutMillis(),
                    params.getTimeoutMillis(), params.getTimeoutMillis());
            if (!response.isSuccessful()) {
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "渠道[{}] HTTP 状态异常: status={}, body={}",
                        spec.getChannelCode(), response.getStatusCode(), response.getBody());
            }
            JsonNode root = JsonUtils.readTree(response.getBody());
            if (root == null) {
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "渠道[{}] 返回空或非 JSON 报文", spec.getChannelCode());
            }
            return view(root);
        } catch (HttpTransportException e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "渠道[" + spec.getChannelCode() + "] 传输失败: " + e.getMessage(), e);
        }
    }

    private View view(JsonNode root) {
        View v = new View();
        v.code = text(root, "code", "errcode");
        v.message = text(root, "message", "msg", "errmsg");
        v.reqId = text(root, "reqId", "requestId", "serialNo");
        v.data = root.path("data");
        v.invoiceNo = text(v.data, "fphm", "invoiceNo", "fpbm", "invoiceNumber");
        v.xml = text(v.data, "xml", "xmlContent", "fpxml", "xmlData");
        v.success = root.path("success").asBoolean(false) || isSuccessCode(v.code);
        return v;
    }

    private InvoiceResult toInvoiceResult(View v) {
        if (spec.getAsyncCode() != null && spec.getAsyncCode().equals(v.code)) {
            return InvoiceResult.accepted(v.reqId, true);
        }
        if (!v.success) {
            return InvoiceResult.failed(v.code, v.message);
        }
        if (StringUtils.isNotBlank(v.xml)) {
            return InvoiceResult.succeeded(v.reqId, v.invoiceNo, null, LocalDate.now(),
                    Collections.singletonList(
                            new InvoiceFile().setType(InvoiceFileType.XML).setContent(v.xml)));
        }
        // 只拿到受理号/票号而未拿到 XML 原件：保持异步，等查单或文件下载补齐
        return InvoiceResult.accepted(firstNonBlank(v.reqId, v.invoiceNo), true);
    }

    private boolean isSuccessCode(String code) {
        if (StringUtils.isBlank(code)) {
            return false;
        }
        for (String candidate : spec.getSuccessCodes()) {
            if (candidate != null && candidate.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    private RestInvoiceParams params(InvoiceContext ctx) {
        InvoiceChannelParams raw = ctx.requireConfig().getParams();
        if (!(raw instanceof RestInvoiceParams)) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "渠道[{}]参数类型错装，期望 RestInvoiceParams", spec.getChannelCode());
        }
        return (RestInvoiceParams) raw;
    }

    /** 渠道状态串 → 框架状态：未知值不迁移状态（返回 null 只补字段），等下次查单/回调收敛。 */
    private static InvoiceState parseInvoiceState(String value) {
        try {
            return InvoiceState.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            if ("SUCCESS".equalsIgnoreCase(value) || "ISSUED".equalsIgnoreCase(value)) {
                return InvoiceState.SUCCEEDED;
            }
            log.warn("未知状态串[{}]，本次不迁移状态，等下次查单/回调收敛", value);
            return null;
        }
    }

    private static RedApplyState redState(String value) {
        if (StringUtils.isBlank(value)) {
            return RedApplyState.WAITING_CONFIRM;
        }
        String upper = value.trim().toUpperCase();
        if ("CONFIRMED".equals(upper) || "SUCCESS".equals(upper) || "2".equals(upper)) {
            return RedApplyState.CONFIRMED;
        }
        if ("REJECTED".equals(upper) || "3".equals(upper)) {
            return RedApplyState.REJECTED;
        }
        if ("EXPIRED".equals(upper) || "4".equals(upper)) {
            return RedApplyState.EXPIRED;
        }
        if ("ISSUED".equals(upper)) {
            return RedApplyState.ISSUED;
        }
        if ("FAILED".equals(upper)) {
            return RedApplyState.FAILED;
        }
        return RedApplyState.WAITING_CONFIRM;
    }

    private static boolean contains(Set<InvoiceFileType> types, InvoiceFileType type) {
        return types == null || types.isEmpty() || types.contains(type);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && text.length() > 0) {
                    return text;
                }
            }
        }
        return null;
    }

    private static LocalDateTime dateTime(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (Exception e) {
            log.debug("时间字段解析失败，按 null 处理，原值: {}", value, e);
            return null;
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

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 渠道响应的统一视图。 */
    private static final class View {
        private String code;
        private String message;
        private String reqId;
        private boolean success;
        private JsonNode data;
        private String invoiceNo;
        private String xml;
    }
}
