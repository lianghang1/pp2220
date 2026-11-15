package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
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
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsCallbackParser;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsCodec;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsGateway;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.support.BaiwangJsResponse;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.AuthType;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 百旺金穗云「税务共享销项」渠道实现（按用户指定，以语雀官方公开文档为准）。
 *
 * <p><b>与通用 REST 适配器的差异</b>：本渠道协议特殊，故不复用
 * {@code AbstractRestInvoiceChannel}，而是独立实现全部能力，把协议细节封闭在本包内：</p>
 * <ul>
 *   <li>请求为 JSON 经 Base64(UTF-8) 转码的字符串，Content-Type 仍为 application/json；</li>
 *   <li>网关签名 {@code Base64(HmacSHA256(sign_str, SECRET))}，每次调用重算（Date/nonce 变化）；</li>
 *   <li>开票、红冲、授权均为<b>异步</b>接口，同步响应只表示受理，结果走
 *       {@code text/plain} Base64 回调，按 {@code ywlxdm} 分流；</li>
 *   <li>XML 法定原件不在开票回调里，而在独立的 {@code qdbswj} 版式回调中给出下载地址；</li>
 *   <li>{@code status=1004} 需要登录 / {@code 1003} 需要实人认证，映射框架 WAITING_AUTH 挂起态。</li>
 * </ul>
 *
 * <p><b>版本</b>：开票与查单默认 V2（{@code /cloud/v2/applyInvoice}、
 * {@code /cloud/v2/queryOrder}），V1 保留完整实现（含合并字段报文差异），
 * 通过 {@code attrs.apiVersion=V1} 切换；其余接口文档只有单一版本。</p>
 *
 * <p>实现无状态、线程安全；所有运行态数据从 {@link InvoiceContext} 取得。</p>
 *
 * @author lianghang
 */
@Slf4j
public class BaiwangJsChannelProvider implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceFileFetcher, InvoiceRedHandler,
        AuthSessionHandler, InvoiceCallbackHandler {

    private static final long RED_CONFIRM_HOURS = 72L;

    @Override
    public String channelCode() {
        return BaiwangJsSpec.CODE;
    }

    @Override
    public ChannelTier channelTier() {
        return ChannelTier.SERVICE_API;
    }

    @Override
    public Set<InvoiceType> supportedTypes() {
        return EnumSet.of(InvoiceType.DIGITAL_VAT_SPECIAL, InvoiceType.DIGITAL_VAT_GENERAL,
                InvoiceType.VAT_SPECIAL, InvoiceType.VAT_GENERAL, InvoiceType.VEHICLE_SALES);
    }

    // ==================== 配置解析 ====================

    @Override
    public InvoiceChannelParams parseParams(Map<String, String> properties) {
        Map<String, String> props = properties == null
                ? Collections.<String, String>emptyMap() : properties;
        BaiwangJsParams params = new BaiwangJsParams()
                .setGatewayKey(first(props, "gatewayKey", "appKey", "keyId"))
                .setGatewaySecret(first(props, "gatewaySecret", "appSecret", "keySecret"))
                .setQyId(first(props, "qyId", "enterpriseId"))
                .setSellerTaxNo(first(props, "sellerTaxNo", "nsrsbh", "sksbh"))
                .setDlzh(first(props, "dlzh", "loginAccount"))
                .setKpr(first(props, "kpr", "drawer"))
                .setSqr(first(props, "sqr", "applicant"))
                .setSellerName(props.get("sellerName"))
                .setSellerAddress(props.get("sellerAddress"))
                .setSellerPhone(props.get("sellerPhone"))
                .setSellerBank(props.get("sellerBank"))
                .setSellerBankAccount(props.get("sellerBankAccount"))
                .setBaseUrl(firstNonBlank(props.get("baseUrl"), BaiwangJsSpec.DEFAULT_BASE_URL))
                .setUserAgent(firstNonBlank(props.get("userAgent"), BaiwangJsSpec.DEFAULT_UA))
                .setTimeoutMillis(parseLong(props.get("timeoutMillis"), 30000L))
                .setSandbox(Boolean.parseBoolean(props.get("sandbox")))
                .setApiVersion(BaiwangJsApiVersion.parse(props.get("apiVersion")))
                .setAttrs(new LinkedHashMap<String, String>(props));

        params.setChannelAppId(firstNonBlank(
                first(props, "channelAppId", "gatewayKey", "appKey"), params.getQyId()));
        params.setHost(hostOf(params.getBaseUrl()));

        Map<String, String> typeCodes = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith("fplxdm.")) {
                typeCodes.put(entry.getKey().substring("fplxdm.".length()), entry.getValue());
            }
        }
        params.setInvoiceTypeCodes(typeCodes);
        return params;
    }

    // ==================== 能力暴露 ====================

    @Override
    public InvoiceIssuer issuer() {
        return this;
    }

    @Override
    public InvoiceQuerier querier() {
        return this;
    }

    @Override
    public InvoiceFileFetcher fileFetcher() {
        return this;
    }

    @Override
    public InvoiceRedHandler redHandler() {
        return this;
    }

    @Override
    public AuthSessionHandler authHandler() {
        return this;
    }

    @Override
    public InvoiceCallbackHandler callbackHandler() {
        return this;
    }

    // ==================== 蓝字开票 ====================

    @Override
    public InvoiceResult issue(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = BaiwangJsCodec.ddlsh(invoice);
        Map<String, Object> body = BaiwangJsCodec.issueBody(ctx, params, ddlsh);
        BaiwangJsResponse response = gateway(params).call("issuePath",
                BaiwangJsSpec.ISSUE_V2, BaiwangJsSpec.ISSUE_V1, body);
        log.debug("百旺金穗云开票受理: ddlsh={}, version={}, code={}",
                ddlsh, params.getApiVersion(), response.getCode());
        return BaiwangJsCodec.acceptedOrFailed(response, ddlsh);
    }

    // ==================== 查单 ====================

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(invoice.getChannelOrderNo(),
                BaiwangJsCodec.ddlsh(invoice));
        Map<String, Object> body = BaiwangJsCodec.queryBody(params, ddlsh);
        BaiwangJsResponse response = gateway(params).call("queryPath",
                BaiwangJsSpec.QUERY_V2, BaiwangJsSpec.QUERY_V1, body);
        if (!response.isAccepted()) {
            // 查询失败不推翻在途单据，保持 PROCESSING 由回调/下次轮询推进
            return InvoiceResult.accepted(ddlsh, true);
        }
        return queryResult(response, ddlsh);
    }

    /** 查单响应：仅当明确给出成功状态与法定原件地址时才推进 SUCCEEDED，否则保持挂起。 */
    private InvoiceResult queryResult(BaiwangJsResponse response, String ddlsh) {
        JsonNode data = response.getRoot() == null ? null
                : firstNonNull(response.getRoot().get("data"), response.getRoot().get("Data"));
        String status = BaiwangJsCodec.text(data, "status");
        List<InvoiceFile> files = layoutFiles(data);
        boolean hasXml = false;
        for (InvoiceFile file : files) {
            if (file.getType() == InvoiceFileType.XML && file.hasPayload()) {
                hasXml = true;
            }
        }
        if (BaiwangJsSpec.ST_SUCCESS.equals(status) && hasXml) {
            return InvoiceResult.succeeded(ddlsh,
                    BaiwangJsCodec.text(data, "qdfphm", "fphm"),
                    BaiwangJsCodec.text(data, "fpdm"),
                    null, files);
        }
        if (BaiwangJsSpec.ST_FAILED.equals(status)) {
            return InvoiceResult.failed("QUERY_FAILED",
                    BaiwangJsCodec.text(data, "ycyy", "message"));
        }
        return InvoiceResult.accepted(ddlsh, true);
    }

    // ==================== 版式文件 ====================

    /**
     * 版式文件：百旺销项侧没有独立的「下载地址查询」接口，文件地址由
     * {@code qdbswj} 异步回调给出。这里按单据 attrs 中已记录的 URL 归一化，
     * 不编造接口；若宿主未落库 URL，返回空列表，由编排层等待版式回调。
     */
    @Override
    public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
        Invoice invoice = ctx.requireInvoice();
        Map<String, String> attrs = invoice.getAttrs();
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (attrs == null) {
            return files;
        }
        addAttrUrl(files, attrs, InvoiceFileType.XML, "xmlurl", "xmlUrl", "baiwangjs.xmlurl");
        addAttrUrl(files, attrs, InvoiceFileType.OFD, "ofdurl", "ofdUrl");
        addAttrUrl(files, attrs, InvoiceFileType.PDF, "pdf", "pdfUrl");
        if (types == null || types.isEmpty()) {
            return files;
        }
        List<InvoiceFile> filtered = new ArrayList<InvoiceFile>();
        for (InvoiceFile file : files) {
            if (types.contains(file.getType())) {
                filtered.add(file);
            }
        }
        return filtered;
    }

    // ==================== 两段红冲 ====================

    @Override
    public RedApplyResult apply(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = redDdlsh(apply);
        Map<String, Object> body = BaiwangJsCodec.redApplyBody(ctx, params, ddlsh);
        BaiwangJsResponse response = gateway(params).call("redApplyPath",
                BaiwangJsSpec.RED_APPLY, null, body);
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_APPLY_FAILED"),
                    response.getReason() == null ? response.getMessage() : response.getReason());
        }
        String confirmNo = response.dataText("hzfpxxqrdbh", "hzqrdh");
        String stateCode = response.dataText("hzqrxxztDm", "hzqrxxzt");
        RedApplyResult result = RedApplyResult.waiting(ddlsh,
                LocalDateTime.now().plusHours(RED_CONFIRM_HOURS)).setRedConfirmNo(confirmNo);
        // 乐企通道：01 无需确认 / 04 购销双方已确认 → 可直接进入可开具状态
        if ("01".equals(stateCode) || "04".equals(stateCode)) {
            result.setState(RedApplyState.CONFIRMED).setRedConfirmNo(confirmNo);
        }
        return result;
    }

    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(apply == null ? null : apply.getChannelOrderNo(),
                redDdlsh(apply));
        Map<String, Object> body = BaiwangJsCodec.redQueryBody(params, ddlsh);
        BaiwangJsResponse response = gateway(params).call("redQueryPath",
                BaiwangJsSpec.RED_QUERY, null, body);
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_QUERY_FAILED"),
                    response.getReason() == null ? response.getMessage() : response.getReason());
        }
        String status = response.dataText("status");
        String confirmNo = response.dataText("hzfpxxqrdbh", "hzqrdh");
        RedApplyResult result = new RedApplyResult().setRedConfirmNo(confirmNo);
        if ("1".equals(status) || "2".equals(status) || "1004".equals(status)
                || StringUtils.isBlank(status)) {
            result.setState(RedApplyState.WAITING_CONFIRM)
                    .setConfirmDeadline(LocalDateTime.now().plusHours(RED_CONFIRM_HOURS));
        } else if ("3".equals(status)) {
            result.setState(RedApplyState.REJECTED).setErrorMsg("红字确认单已作废");
        } else if ("4".equals(status)) {
            result.setState(RedApplyState.CONFIRMED);
        } else if ("5".equals(status) || "2".equals(response.getCode())) {
            result.setState(RedApplyState.FAILED).setErrorMsg(response.dataText("ycyy"));
        } else {
            result.setState(RedApplyState.WAITING_CONFIRM);
        }
        return result;
    }

    /**
     * 第二段：平台在对方确认后由渠道侧自动开具红字发票，公开文档没有独立的
     * 「确认后开具」接口，结果通过 {@code fpkj}(带 yqdfphm) / {@code qdbswj} 回调返回。
     * 因此这里只做一次结果核对并返回异步受理，不重复调用申请接口（避免重复红冲），
     * 由回调把红字单据推进到 SUCCEEDED。
     */
    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(apply == null ? null : apply.getChannelOrderNo(),
                redDdlsh(apply));
        gateway(params).call("redQueryPath", BaiwangJsSpec.RED_QUERY, null,
                BaiwangJsCodec.redQueryBody(params, ddlsh));
        return InvoiceResult.accepted(ddlsh, true);
    }

    // ==================== 授权会话 ====================

    @Override
    public AuthResult start(InvoiceContext ctx) {
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        // 第一步：电子税务局登录（不带 sms）
        BaiwangJsResponse login = gateway(params).call("authStartPath",
                BaiwangJsSpec.LOGIN, null,
                BaiwangJsCodec.loginBody(params, null));
        if (login.isSmsSent() || login.needsLogin()) {
            return AuthResult.pending(new AuthChallenge()
                    .setAuthType(AuthType.SMS)
                    .setChallengeText(firstNonBlank(login.getMessage(),
                            "短信验证码已发送，请提交验证码完成电子税务局登录"))
                    .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
        }
        if (!login.isAccepted()) {
            return AuthResult.rejected(nullTo(login.getCode(), "AUTH_START_FAILED"),
                    firstNonBlank(login.getReason(), login.getMessage()));
        }
        // 已登录：请求实人认证二维码
        String ddlsh = BaiwangJsCodec.ddlsh(ctx.requireInvoice());
        BaiwangJsResponse qr = gateway(params).call("qrCodePath",
                BaiwangJsSpec.QRCODE_SYNC, null,
                BaiwangJsCodec.qrCodeBody(params, ddlsh));
        String ewm = qr.dataText("ewm", "qrcode", "qrcodeUrl");
        String rzid = qr.dataText("rzid");
        Map<String, String> extra = new LinkedHashMap<String, String>();
        if (StringUtils.isNotBlank(rzid)) {
            extra.put("rzid", rzid);
        }
        if (qr.needsAuth() || StringUtils.isNotBlank(ewm)) {
            return AuthResult.pending(new AuthChallenge()
                    .setAuthType(AuthType.FACE)
                    .setChallengeUrl(ewm)
                    .setChallengeText("请使用电子税务局 App 扫码完成实人认证")
                    .setExtra(extra)
                    .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
        }
        return AuthResult.verified(InvoiceState.SUBMITTING);
    }

    @Override
    public AuthResult submit(InvoiceContext ctx, AuthSubmission submission) {
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        if (submission != null && StringUtils.isNotBlank(submission.getCaptcha())) {
            BaiwangJsResponse login = gateway(params).call("authSubmitPath",
                    BaiwangJsSpec.LOGIN, null,
                    BaiwangJsCodec.loginBody(params, submission.getCaptcha()));
            if (login.isAccepted()) {
                return AuthResult.verified(InvoiceState.SUBMITTING);
            }
            return AuthResult.rejected(nullTo(login.getCode(), "AUTH_SUBMIT_FAILED"),
                    firstNonBlank(login.getReason(), login.getMessage()));
        }
        if (submission != null && StringUtils.isNotBlank(submission.getToken())) {
            // 扫脸结果由 3.4.5 查询；该接口为异步（cxnsrfx 回调），此处仅登记受理
            String ddlsh = BaiwangJsCodec.ddlsh(ctx.requireInvoice());
            BaiwangJsResponse risk = gateway(params).call("riskInfoPath",
                    BaiwangJsSpec.RISK_INFO, null,
                    BaiwangJsCodec.riskInfoBody(params, ddlsh));
            if (risk.isAccepted()) {
                return AuthResult.pending(new AuthChallenge()
                        .setAuthType(AuthType.FACE)
                        .setChallengeText("已查询认证结果，等待渠道 cxnsrfx 异步回调确认")
                        .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
            }
            return AuthResult.rejected(nullTo(risk.getCode(), "AUTH_SUBMIT_FAILED"),
                    firstNonBlank(risk.getReason(), risk.getMessage()));
        }
        return AuthResult.rejected("AUTH_CREDENTIAL_MISSING", "短信验证码或扫码 token 不能为空");
    }

    // ==================== 回调 ====================

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        return BaiwangJsCallbackParser.parse(raw, config);
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        return BaiwangJsCallbackParser.reply(success, message);
    }

    // ==================== 内部工具 ====================

    private BaiwangJsGateway gateway(BaiwangJsParams params) {
        return new BaiwangJsGateway(params);
    }

    private BaiwangJsParams params(InvoiceContext ctx) {
        ChannelConfig config = ctx.getConfig();
        if (config != null && config.getParams() instanceof BaiwangJsParams) {
            return (BaiwangJsParams) config.getParams();
        }
        if (config != null) {
            return (BaiwangJsParams) parseParams(config.getAttrs());
        }
        throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                "百旺金穗云渠道配置缺失");
    }

    private void requireCredentials(BaiwangJsParams params) {
        if (StringUtils.isBlank(params.getGatewayKey())
                || StringUtils.isBlank(params.getGatewaySecret())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百旺金穗云缺少 gatewayKey/gatewaySecret，请在 attrs 中配置网关密钥");
        }
        if (StringUtils.isBlank(params.getQyId())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百旺金穗云缺少 qyId（企业唯一标识），请通过 3.1.1 接口查询后配置");
        }
    }

    private String redDdlsh(RedApply apply) {
        return apply == null || StringUtils.isBlank(apply.getBizRedNo())
                ? "RED-" + System.currentTimeMillis() : apply.getBizRedNo();
    }

    private List<InvoiceFile> layoutFiles(JsonNode node) {
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (node == null) {
            return files;
        }
        addUrl(files, node, InvoiceFileType.XML, "xmlurl", "xml");
        addUrl(files, node, InvoiceFileType.OFD, "ofdurl");
        addUrl(files, node, InvoiceFileType.PDF, "pdf");
        return files;
    }

    private void addUrl(List<InvoiceFile> files, JsonNode node, InvoiceFileType type,
                        String... names) {
        String url = BaiwangJsCodec.text(node, names);
        if (StringUtils.isNotBlank(url)) {
            files.add(new InvoiceFile().setType(type).setUrl(url));
        }
    }

    private void addAttrUrl(List<InvoiceFile> files, Map<String, String> attrs,
                            InvoiceFileType type, String... names) {
        for (String name : names) {
            String url = attrs.get(name);
            if (StringUtils.isNotBlank(url)) {
                files.add(new InvoiceFile().setType(type).setUrl(url));
                return;
            }
        }
    }

    private static JsonNode firstNonNull(JsonNode a, JsonNode b) {
        return a != null && !a.isNull() ? a : b;
    }

    private static String first(Map<String, String> props, String... names) {
        for (String name : names) {
            String value = props.get(name);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
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

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 从 baseUrl 解析 Host 头（本地化部署时与签名 host 保持一致）。 */
    private static String hostOf(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null) {
                return BaiwangJsSpec.DEFAULT_HOST;
            }
            return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
        } catch (Exception e) {
            return BaiwangJsSpec.DEFAULT_HOST;
        }
    }
}
