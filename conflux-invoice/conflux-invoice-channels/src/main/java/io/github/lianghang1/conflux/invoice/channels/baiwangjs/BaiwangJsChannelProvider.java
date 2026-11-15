package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.api.RedAvailableResult;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
import io.github.lianghang1.conflux.invoice.channel.AuthSessionHandler;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceCallbackHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceDeliverer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRepairer;
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
        AuthSessionHandler, InvoiceCallbackHandler, InvoiceDeliverer, InvoiceRepairer {

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

    /**
     * 3.1.1 查询企业唯一标识（配置期工具，BW-01）。
     *
     * <p>框架不自动调用，避免启动期外部依赖。使用方首次接入时传入至少含
     * {@code gatewayKey}/{@code gatewaySecret} 的配置调用一次，把返回的 {@code qyId}
     * 写入 {@code attrs.qyId} 即可。返回 null 表示渠道未返回（配置有误或企业未开户）。</p>
     *
     * <p><b>为什么必须显式传凭据</b>：该接口本身用于换取 qyId，签名只需网关
     * KEY/SECRET（不需要 qyId）；若从空配置构造参数，网关签名会因缺 KEY/SECRET
     * 直接抛 CONFIG_MISSING，因此这里要求调用方显式传入并做前置校验。</p>
     */
    public String queryUniqueSign(Map<String, String> credentials,
                                  String sellerName, String sellerTaxNo) {
        BaiwangJsParams params = (BaiwangJsParams) parseParams(credentials);
        if (StringUtils.isBlank(params.getGatewayKey())
                || StringUtils.isBlank(params.getGatewaySecret())) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "3.1.1 查询企业唯一标识需要网关凭据：请提供 gatewayKey/gatewaySecret");
        }
        return new BaiwangJsGateway(params).queryUniqueSign(sellerName, sellerTaxNo);
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

    @Override
    public InvoiceDeliverer deliverer() {
        return this;
    }

    @Override
    public InvoiceRepairer repairer() {
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
        JsonNode data = BaiwangJsCodec.dataNode(response);
        String status = BaiwangJsCodec.text(data, "status");
        List<InvoiceFile> files = BaiwangJsCodec.layoutFiles(data);
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
     * 版式文件：优先调用 3.4.15 电票版式文件查询（按票号）/ 3.4.8 电子发票下载地址查询（按 ddlsh），
     * 二者均为同步接口，字段名差异由 {@link BaiwangJsCodec#layoutFiles} 兼容。
     *
     * <p>本方法会在编排层置 {@code SUCCEEDED} 前被调用，因此<b>渠道查询失败一律降级</b>到
     * {@code qdbswj} 回调已记录在 attrs 的 URL，绝不因辅助查询异常阻断主流程；
     * 两者都拿不到时返回空列表，由编排层的 XML 法定原件强约束兜底。</p>
     */
    @Override
    public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
        Invoice invoice = ctx.requireInvoice();
        List<InvoiceFile> files = fetchFromChannel(ctx, invoice);
        if (files.isEmpty()) {
            files = filesFromAttrs(invoice);
        }
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

    /** 同步查询渠道版式文件；缺凭据或任何异常都返回空列表（由调用方降级）。 */
    private List<InvoiceFile> fetchFromChannel(InvoiceContext ctx, Invoice invoice) {
        BaiwangJsParams params = params(ctx);
        if (!hasCredentials(params)) {
            return Collections.emptyList();
        }
        String ddlsh = firstNonBlank(invoice.getChannelOrderNo(),
                BaiwangJsCodec.ddlsh(invoice));
        try {
            BaiwangJsResponse response;
            if (StringUtils.isNotBlank(invoice.getInvoiceNo())) {
                Map<String, Object> body = BaiwangJsCodec.layoutQueryBody(params, ddlsh,
                        invoice.getInvoiceCode(), invoice.getInvoiceNo());
                response = gateway(params).call("layoutQueryPath",
                        BaiwangJsSpec.LAYOUT_QUERY, null, body);
            } else {
                Map<String, Object> body = BaiwangJsCodec.downloadBody(params, ddlsh);
                response = gateway(params).call("downloadPath",
                        BaiwangJsSpec.INVOICE_DOWNLOAD, null, body);
            }
            if (!response.isAccepted()) {
                return Collections.emptyList();
            }
            return BaiwangJsCodec.layoutFiles(BaiwangJsCodec.dataNode(response));
        } catch (RuntimeException e) {
            log.warn("百旺金穗云版式文件查询失败，降级使用回调已记录地址: ddlsh={}, err={}",
                    ddlsh, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** qdbswj 版式回调落到单据 attrs 的 URL。 */
    private List<InvoiceFile> filesFromAttrs(Invoice invoice) {
        Map<String, String> attrs = invoice.getAttrs();
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (attrs == null) {
            return files;
        }
        addAttrUrl(files, attrs, InvoiceFileType.XML, "xmlurl", "xmlUrl", "baiwangjs.xmlurl");
        addAttrUrl(files, attrs, InvoiceFileType.OFD, "ofdurl", "ofdUrl");
        addAttrUrl(files, attrs, InvoiceFileType.PDF, "pdf", "pdfUrl");
        return files;
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

    /**
     * 3.3.15 红字确认单状态查询（同步，返回 hzqrxxztDm）。
     *
     * <p>文档 3.3.15 明确「不支持结果回调」，是查询确认状态的对症接口；
     * 3.3.4 数电红冲结果查询保留为 V1/兼容路径，可由 {@code attrs.redQueryPath} 覆盖。
     * 无确认单编号时退回 3.3.4，避免用空编号调用 3.3.15。</p>
     */
    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(apply == null ? null : apply.getChannelOrderNo(),
                redDdlsh(apply));
        String confirmNo = apply == null ? null : apply.getRedConfirmNo();

        BaiwangJsResponse response;
        if (StringUtils.isNotBlank(confirmNo)) {
            response = gateway(params).call("redStatusPath",
                    BaiwangJsSpec.RED_STATUS_QUERY, null,
                    BaiwangJsCodec.redStatusBody(params, ddlsh, confirmNo));
        } else {
            response = gateway(params).call("redQueryPath",
                    BaiwangJsSpec.RED_QUERY, null,
                    BaiwangJsCodec.redQueryBody(params, ddlsh));
        }
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_QUERY_FAILED"),
                    response.getReason() == null ? response.getMessage() : response.getReason());
        }
        // 3.3.15 用 hzqrxxztDm（红字确认状态），3.3.4 回退用 status
        String state = response.dataText("hzqrxxztDm", "hzqrxxzt", "status");
        String returnedNo = response.dataText("hzfpxxqrdbh", "hzqrdh");
        RedApplyResult result = new RedApplyResult().setRedConfirmNo(
                StringUtils.isNotBlank(returnedNo) ? returnedNo : confirmNo);
        if ("01".equals(state) || "04".equals(state) || "4".equals(state)) {
            result.setState(RedApplyState.CONFIRMED);
        } else if ("3".equals(state) || "05".equals(state)) {
            result.setState(RedApplyState.REJECTED).setErrorMsg("红字确认单已作废或被拒绝");
        } else if ("5".equals(state) || "2".equals(response.getCode())) {
            result.setState(RedApplyState.FAILED).setErrorMsg(response.dataText("ycyy"));
        } else {
            result.setState(RedApplyState.WAITING_CONFIRM)
                    .setConfirmDeadline(LocalDateTime.now().plusHours(RED_CONFIRM_HOURS));
        }
        return result;
    }

    /**
     * 第二段 3.3.12 数电红冲开具：确认单已确认后真正向渠道发起红字开具。
     * 该接口为异步，同步响应只表示受理，结果由 {@code fpkj}(带 yqdfphm) /
     * {@code qdbswj} 回调推进到 SUCCEEDED。
     */
    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(apply == null ? null : apply.getChannelOrderNo(),
                redDdlsh(apply));
        String confirmNo = apply == null ? null : apply.getRedConfirmNo();
        Map<String, Object> body = BaiwangJsCodec.redIssueBody(params, ddlsh, confirmNo);
        BaiwangJsResponse response = gateway(params).call("redIssuePath",
                BaiwangJsSpec.RED_ISSUE, null, body);
        return BaiwangJsCodec.acceptedOrFailed(response, ddlsh);
    }

    /** 3.3.16 红字确认单确认或拒绝（qrlx=Y/N，同步，不支持回调）。 */
    @Override
    public RedApplyResult confirm(InvoiceContext ctx, RedConfirmRequest request) {
        RedApply apply = ctx.getRedApply();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String confirmNo = apply == null ? null : apply.getRedConfirmNo();
        if (StringUtils.isBlank(confirmNo) && request != null && request.getExtra() != null) {
            confirmNo = request.getExtra().get("hzfpxxqrdbh");
        }
        Map<String, Object> body = BaiwangJsCodec.redConfirmBody(params, confirmNo,
                request != null && request.isAccept());
        BaiwangJsResponse response = gateway(params).call("redConfirmPath",
                BaiwangJsSpec.RED_CONFIRM, null, body);
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_CONFIRM_FAILED"),
                    response.getReason() == null ? response.getMessage() : response.getReason());
        }
        String state = response.dataText("hzqrxxztDm", "hzqrxxzt");
        RedApplyResult result = new RedApplyResult().setRedConfirmNo(confirmNo);
        if ("01".equals(state) || "04".equals(state) || "4".equals(state)
                || (request != null && request.isAccept() && StringUtils.isBlank(state))) {
            result.setState(RedApplyState.CONFIRMED);
        } else if (request != null && !request.isAccept()) {
            result.setState(RedApplyState.REJECTED)
                    .setErrorMsg(request.getReason() == null ? "我方拒绝红字确认单" : request.getReason());
        } else {
            result.setState(RedApplyState.WAITING_CONFIRM)
                    .setConfirmDeadline(LocalDateTime.now().plusHours(RED_CONFIRM_HOURS));
        }
        return result;
    }

    /**
     * 3.3.17 可冲销明细查询（异步，回调 ywlxdm=hzcx）。
     * 同步响应只表示受理，因此返回 asyncAccepted=true，编排层不据此阻断红冲。
     */
    @Override
    public RedAvailableResult checkRedAvailable(InvoiceContext ctx) {
        Invoice original = ctx.getInvoice();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        String ddlsh = firstNonBlank(original == null ? null : original.getChannelOrderNo(),
                original == null ? "HZCX-" + System.currentTimeMillis()
                        : BaiwangJsCodec.ddlsh(original));
        String invoiceDate = original == null || original.getInvoiceDate() == null
                ? null : original.getInvoiceDate().toString();
        Map<String, Object> body = BaiwangJsCodec.redAvailableBody(params, ddlsh,
                original, invoiceDate, null, "2");
        BaiwangJsResponse response = gateway(params).call("redAvailablePath",
                BaiwangJsSpec.RED_AVAILABLE_QUERY, null, body);
        RedAvailableResult result = new RedAvailableResult()
                .setAvailable(true)
                .setAsyncAccepted(response.isAccepted())
                .setChannelOrderNo(ddlsh)
                .setRawPayload(response.getRawBody());
        if (!response.isAccepted()) {
            // 查询失败不阻断红冲（渠道可能不支持该场景），仅登记错误供排障
            result.setErrorCode(nullTo(response.getCode(), "RED_AVAILABLE_FAILED"))
                    .setErrorMsg(response.getReason() == null
                            ? response.getMessage() : response.getReason());
        }
        return result;
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
        // 已登录：请求实人认证二维码。默认 3.4.6 同步；attrs.qrMode=ASYNC 走 3.4.5 异步
        String ddlsh = BaiwangJsCodec.ddlsh(ctx.requireInvoice());
        boolean asyncQr = "ASYNC".equalsIgnoreCase(
                params.getAttrs() == null ? null : params.getAttrs().get("qrMode"));
        BaiwangJsResponse qr;
        if (asyncQr) {
            qr = gateway(params).call("qrCodeAsyncPath",
                    BaiwangJsSpec.QRCODE_ASYNC, null,
                    BaiwangJsCodec.qrCodeAsyncBody(params, ddlsh, "1"));
        } else {
            qr = gateway(params).call("qrCodePath",
                    BaiwangJsSpec.QRCODE_SYNC, null,
                    BaiwangJsCodec.qrCodeBody(params, ddlsh));
        }
        String ewm = qr.dataText("ewm", "qrcode", "qrcodeUrl");
        String rzid = qr.dataText("rzid");
        Map<String, String> extra = new LinkedHashMap<String, String>();
        if (StringUtils.isNotBlank(rzid)) {
            extra.put("rzid", rzid);
        }
        if (asyncQr && qr.isAccepted()) {
            // 3.4.5 为异步接口，二维码由 ewmrz 回调带回；此处只登记受理
            extra.put("qrMode", "ASYNC");
            return AuthResult.pending(new AuthChallenge()
                    .setAuthType(AuthType.FACE)
                    .setChallengeText("实人认证二维码将由渠道 ewmrz 回调返回，请稍候")
                    .setExtra(extra)
                    .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
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
            // 3.4.14 认证结果查询：异步接口（结果走 cxnsrfx 回调），但同步响应若直接
            // 带回 sfsl（是否刷脸）即可收敛；sfsl=N 表示无需刷脸 → 直接通过。
            String ddlsh = BaiwangJsCodec.ddlsh(ctx.requireInvoice());
            BaiwangJsResponse risk = gateway(params).call("riskInfoPath",
                    BaiwangJsSpec.RISK_INFO, null,
                    BaiwangJsCodec.riskInfoBody(params, ddlsh));
            if (risk.isAccepted()) {
                String sfsl = risk.dataText("sfsl");
                if ("N".equalsIgnoreCase(sfsl)) {
                    return AuthResult.verified(InvoiceState.SUBMITTING);
                }
                if (risk.needsAuth() || "Y".equalsIgnoreCase(sfsl)) {
                    return AuthResult.pending(new AuthChallenge()
                            .setAuthType(AuthType.FACE)
                            .setChallengeText("纳税人风险校验触发实人认证，请使用电子税务局 App 扫脸")
                            .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
                }
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

    // ==================== 发票交付（可选能力，BW-42 / 3.4.7） ====================

    /**
     * 3.4.7 发票短信邮箱重发（同步）。
     *
     * <p>编排层已校验手机号/邮箱至少其一；票号优先取本地单据已归档的
     * {@code invoiceCode/invoiceNo}，无本地单据或换渠道补单时允许由
     * {@code request.extra} 的 {@code fpDm/fpHm/qdfphm} 覆盖。</p>
     */
    @Override
    public DeliveryResult resend(InvoiceContext ctx, DeliveryRequest request) {
        Invoice invoice = ctx.requireInvoice();
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        DeliveryRequest req = request == null ? new DeliveryRequest() : request;

        Map<String, String> extra = req.getExtra();
        String invoiceCode = firstNonBlank(invoice.getInvoiceCode(),
                first(extra, "fpDm", "invoiceCode"));
        String invoiceNo = firstNonBlank(invoice.getInvoiceNo(),
                first(extra, "fpHm", "qdfphm", "invoiceNo"));
        String phone = firstNonBlank(req.getPhone(), first(extra, "phone"));
        String email = firstNonBlank(req.getEmail(), first(extra, "email"));
        if (StringUtils.isBlank(invoiceNo)) {
            return DeliveryResult.failed("DELIVERY_NO_REQUIRED",
                    "缺少发票号码，无法重发交付（单据尚未回传票号）");
        }

        Map<String, Object> body = BaiwangJsCodec.deliveryBody(params,
                invoiceCode, invoiceNo, phone, email);
        BaiwangJsResponse response = gateway(params).call("deliveryPath",
                BaiwangJsSpec.DELIVERY_RESEND, null, body);
        DeliveryResult result = response.isAccepted()
                ? DeliveryResult.ok()
                : DeliveryResult.failed(nullTo(response.getCode(), "DELIVERY_FAILED"),
                        response.getReason() == null
                                ? response.getMessage() : response.getReason());
        return result.setRawPayload(response.getRawBody());
    }

    // ==================== 发票修复（可选能力，BW-47/48/49/50） ====================

    /**
     * 3.4.11 / 3.4.11(V2) 发票修复申请（异步，不支持结果回调）。
     *
     * <p>V2 优先、V1 可通过 {@code attrs.apiVersion=V1} 切换，两个版本共用
     * {@code repairPath} 覆盖键，不为版本单开方法。文档明确「不支持结果回调，
     * 请调用 3.4.12 或 3.4.13 查询修复结果」，故申请只返回受理状态，
     * 由 {@link #query(InvoiceContext, RepairRequest)} 跟进。</p>
     */
    @Override
    public RepairResult apply(InvoiceContext ctx, RepairRequest request) {
        RepairRequest req = request == null ? new RepairRequest() : request;
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        Invoice invoice = ctx.getInvoice();
        Map<String, String> extra = req.getExtra();

        String ddlsh = repairOrderNo(req, invoice);
        String invoiceNo = firstNonBlank(req.getInvoiceNo(),
                invoice == null ? null : invoice.getInvoiceNo(),
                first(extra, "fphm", "qdfphm"));
        String typeCode = firstNonBlank(req.getInvoiceTypeCode(),
                first(extra, "fplxdm"),
                invoice == null ? null : params.invoiceTypeCode(invoice.getInvoiceType()));
        if (StringUtils.isBlank(typeCode)) {
            return RepairResult.failed("REPAIR_PARAM_REQUIRED",
                    "发票修复缺少 fplxdm（发票类型代码）");
        }
        String buyerName = firstNonBlank(first(extra, "gmfMc", "buyerName"),
                invoice == null ? null : invoice.getBuyerTitle());
        Map<String, Object> body = BaiwangJsCodec.repairBody(params, ddlsh, typeCode,
                invoiceNo, req.getStartDate(), req.getEndDate(), buyerName);

        BaiwangJsResponse response = gateway(params).call("repairPath",
                BaiwangJsSpec.REPAIR_V2, BaiwangJsSpec.REPAIR_V1, body);
        RepairResult result = response.isAccepted()
                ? RepairResult.accepted(ddlsh)
                : RepairResult.failed(nullTo(response.getCode(), "REPAIR_FAILED"),
                        response.getReason() == null
                                ? response.getMessage() : response.getReason());
        return result.setRawPayload(response.getRawBody());
    }

    /**
     * 3.4.12 发票修复结果查询（同步）：{@code code=0} 已修复到发票、
     * {@code code=-1} 失败或尚未修复到发票（可继续查询）、{@code 1003} 需要登录。
     *
     * <p>3.4.11 文档说明修复结果也可通过 3.4.13「按票号或时间查询」得到（BW-50）。
     * 当 {@code attrs.repairResultMode=BATCH} 或 {@code request.extra.queryMode=BATCH}
     * 时改走 3.4.13，按票号/日期范围组装，复用同一套版式文件与票号归一逻辑。</p>
     */
    @Override
    public RepairResult query(InvoiceContext ctx, RepairRequest request) {
        RepairRequest req = request == null ? new RepairRequest() : request;
        BaiwangJsParams params = params(ctx);
        requireCredentials(params);
        Map<String, String> extra = req.getExtra();
        String ddlsh = firstNonBlank(req.getChannelOrderNo(), first(extra, "ddlsh"));
        if (StringUtils.isBlank(ddlsh)) {
            return RepairResult.failed("REPAIR_PARAM_REQUIRED",
                    "修复结果查询缺少 channelOrderNo/ddlsh");
        }

        boolean batch = "BATCH".equalsIgnoreCase(firstNonBlank(
                first(extra, "queryMode", "repairResultMode"),
                params.getAttrs() == null ? null : params.getAttrs().get("repairResultMode")));
        BaiwangJsResponse response;
        if (batch) {
            Map<String, Object> body = BaiwangJsCodec.queryBatchBody(params,
                    StringUtils.isNotBlank(req.getStartDate()) ? "3" : "0",
                    req.getInvoiceTypeCode(), req.getInvoiceCode(),
                    req.getInvoiceNo(), req.getInvoiceNo(),
                    req.getStartDate(), req.getEndDate(),
                    req.getInvoiceNo(), first(extra, "pageNo"), first(extra, "pageSize"));
            String elecBz = first(extra, "elecBz");
            if (StringUtils.isNotBlank(elecBz)) {
                body.put("elecBz", elecBz);
            }
            response = gateway(params).call("queryBatchPath",
                    BaiwangJsSpec.QUERY_BATCH, null, body);
        } else {
            response = gateway(params).call("repairResultPath",
                    BaiwangJsSpec.REPAIR_RESULT, null,
                    BaiwangJsCodec.repairResultBody(params, ddlsh, first(extra, "elecBz")));
        }
        return BaiwangJsCodec.repairResult(response, ddlsh);
    }

    /** 修复流水号：请求显式指定优先，其次复用单据渠道流水号，否则生成（结果查询需传同一值）。 */
    private String repairOrderNo(RepairRequest req, Invoice invoice) {
        if (StringUtils.isNotBlank(req.getChannelOrderNo())) {
            return req.getChannelOrderNo();
        }
        if (invoice != null && StringUtils.isNotBlank(invoice.getChannelOrderNo())) {
            return invoice.getChannelOrderNo();
        }
        return "REPAIR-" + System.currentTimeMillis();
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
        if (!hasCredentials(params)) {
            if (params == null || StringUtils.isBlank(params.getGatewayKey())
                    || StringUtils.isBlank(params.getGatewaySecret())) {
                throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                        "百旺金穗云缺少 gatewayKey/gatewaySecret，请在 attrs 中配置网关密钥");
            }
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百旺金穗云缺少 qyId（企业唯一标识），请通过 3.1.1 接口查询后配置");
        }
    }

    /** 凭据是否齐备（不抛异常，供可降级的辅助查询使用）。 */
    private boolean hasCredentials(BaiwangJsParams params) {
        return params != null
                && StringUtils.isNotBlank(params.getGatewayKey())
                && StringUtils.isNotBlank(params.getGatewaySecret())
                && StringUtils.isNotBlank(params.getQyId());
    }

    private String redDdlsh(RedApply apply) {
        return apply == null || StringUtils.isBlank(apply.getBizRedNo())
                ? "RED-" + System.currentTimeMillis() : apply.getBizRedNo();
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

    private static String first(Map<String, String> props, String... names) {
        if (props == null) {
            return null;
        }
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
