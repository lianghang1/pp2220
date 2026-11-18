package io.github.lianghang1.conflux.invoice.channels.baiwang;

import com.baiwang.open.entity.request.SOutputinvoiceFastRedRequest;
import com.baiwang.open.entity.request.SOutputinvoiceInvoiceRequest;
import com.baiwang.open.entity.request.SOutputinvoicePushRequest;
import com.baiwang.open.entity.request.SOutputinvoiceQueryRequest;
import com.baiwang.open.entity.request.SOutputinvoiceRetryRequest;
import com.baiwang.open.entity.request.SRedconfirmOperateRequest;
import com.baiwang.open.entity.request.SRedconfirmQueryRequest;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.api.RepairRequest;
import io.github.lianghang1.conflux.invoice.api.RepairResult;
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
import io.github.lianghang1.conflux.invoice.channels.baiwang.support.BaiwangCallbackParser;
import io.github.lianghang1.conflux.invoice.channels.baiwang.support.BaiwangCodec;
import io.github.lianghang1.conflux.invoice.channels.baiwang.support.BaiwangGateway;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 百望云（BOPSDK）渠道实现。
 *
 * <p>基于官方 BOPSDK 的接口组模式（{@code client.sOutputinvoice().invoice(query...)}），
 * 渠道差异封闭在本包内，不复用通用 REST 适配器（百望的签名/token/报文均由 SDK 封装）。：</p>
 * <ul>
 *   <li>token 由 {@link BaiwangGateway} 密码登录获取并缓存（appSecret 仅用于签名）；</li>
 *   <li>开票/红冲为异步受理，结果走回调或查单；只有拿到票号才置 SUCCEEDED；</li>
 *   <li>回调按 {@code method}/{@code type} 分流，XML/PDF 地址在 data 内联归档。</li>
 * </ul>
 *
 * <p>无状态、线程安全，所有运行态从 {@link InvoiceContext} 取得。</p>
 *
 * @author lianghang
 */
@Slf4j
public class BaiwangChannelProvider implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceFileFetcher, InvoiceRedHandler,
        InvoiceCallbackHandler, InvoiceDeliverer, InvoiceRepairer {

    @Override
    public String channelCode() {
        return BaiwangSpec.CODE;
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
        boolean sandbox = Boolean.parseBoolean(props.get("sandbox"));
        BaiwangParams params = new BaiwangParams()
                .setAppKey(first(props, "appKey", "clientId", "gatewayKey"))
                .setAppSecret(first(props, "appSecret", "clientSecret", "gatewaySecret"))
                .setSalt(first(props, "userSalt", "salt"))
                .setUsername(first(props, "username", "user", "tenantAccount"))
                .setPassword(first(props, "password", "pwd"))
                .setAccessToken(first(props, "accessToken", "token"))
                .setSellerTaxNo(first(props, "sellerTaxNo", "nsrsbh"))
                .setSellerName(props.get("sellerName"))
                .setInvoiceTerminalCode(first(props, "invoiceTerminalCode", "dlzh", "数字账号"))
                .setDeviceType(first(props, "deviceType", "deviceCode"))
                .setDrawer(first(props, "drawer", "kpr"))
                .setPayee(first(props, "payee", "skr"))
                .setChecker(first(props, "checker", "fhr"))
                .setSellerAddress(first(props, "sellerAddress", "sellerAddressPhone"))
                .setSellerPhone(props.get("sellerPhone"))
                .setSellerBankName(first(props, "sellerBankName", "sellerBank"))
                .setSellerBankNumber(first(props, "sellerBankNumber", "sellerBankAccount"))
                .setCallbackUrl(first(props, "callBackUrl", "callbackUrl", "notifyUrl"))
                .setServerUrl(firstNonBlank(props.get("serverUrl"), props.get("baseUrl"),
                        sandbox ? BaiwangSpec.SANDBOX_BASE_URL : BaiwangSpec.DEFAULT_BASE_URL))
                .setSandbox(sandbox)
                .setTimeoutMillis(parseLong(props.get("timeoutMillis"), 30000L))
                .setAttrs(new LinkedHashMap<String, String>(props));
        params.setChannelAppId(firstNonBlank(params.getAppKey(), params.getSellerTaxNo()));
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
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        String orderNo = firstNonBlank(invoice.getBizOrderNo(), invoice.getChannelOrderNo());
        BaiwangGateway gateway = gateway(params);
        try {
            String token = gateway.token();
            SOutputinvoiceInvoiceRequest request = BaiwangCodec.issueRequest(ctx, params);
            Object response = gateway.client().sOutputinvoice().invoice(request, token);
            log.debug("百望云开票受理: orderNo={}, success={}", orderNo, BaiwangCodec.isSuccess(response));
            return BaiwangCodec.issueResult(response, orderNo);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云开票");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云开票");
        }
    }

    // ==================== 查单 ====================

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        String orderNo = firstNonBlank(invoice.getBizOrderNo(), invoice.getChannelOrderNo());
        String serialNo = invoice.getChannelOrderNo();
        BaiwangGateway gateway = gateway(params);
        try {
            String token = gateway.token();
            SOutputinvoiceQueryRequest request = BaiwangCodec.queryRequest(params, orderNo, serialNo);
            Object response = gateway.client().sOutputinvoice().query(request, token);
            return BaiwangCodec.queryResult(response, orderNo);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云查单");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云查单");
        }
    }

    // ==================== 版式文件 ====================

    /**
     * 版式文件：优先查单接口（同步）取 xmlUrl/pdfUrl/ofdUrl；
     * 查询失败降级使用回调落在 attrs 的文件地址；辅助查询从不阻断主流程。
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

    private List<InvoiceFile> fetchFromChannel(InvoiceContext ctx, Invoice invoice) {
        BaiwangParams params = params(ctx);
        if (!params.hasCredentials()) {
            return Collections.emptyList();
        }
        String orderNo = firstNonBlank(invoice.getBizOrderNo(), invoice.getChannelOrderNo());
        try {
            BaiwangGateway gateway = gateway(params);
            SOutputinvoiceQueryRequest request = BaiwangCodec.queryRequest(params, orderNo,
                    invoice.getChannelOrderNo());
            Object response = gateway.client().sOutputinvoice().query(request, gateway.token());
            return BaiwangCodec.queryFiles(response);
        } catch (RuntimeException e) {
            log.warn("百望云版式文件查询失败，降级使用回调地址: orderNo={}, err={}",
                    orderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<InvoiceFile> filesFromAttrs(Invoice invoice) {
        Map<String, String> attrs = invoice.getAttrs();
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        if (attrs == null) {
            return files;
        }
        addAttrUrl(files, InvoiceFileType.XML, attrs, "xmlUrl", "xmlurl", "baiwang.xmlUrl");
        addAttrUrl(files, InvoiceFileType.OFD, attrs, "ofdUrl", "ofdurl");
        addAttrUrl(files, InvoiceFileType.PDF, attrs, "pdfUrl", "pdf");
        return files;
    }

    // ==================== 红冲（快捷冲红 + 确认单操作） ====================

    /** 第一段：快捷冲红 fastIssueRedType=0，只生成红字确认单，等待对方确认。 */
    @Override
    public RedApplyResult apply(InvoiceContext ctx) {
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        BaiwangGateway gateway = gateway(params);
        Invoice invoice = ctx.getInvoice();
        RedApply redApply = ctx.getRedApply();
        String orderNo = invoice == null ? null : invoice.getBizOrderNo();
        try {
            SOutputinvoiceFastRedRequest request = BaiwangCodec.fastRedRequest(params, invoice,
                    redApply, BaiwangSpec.FAST_RED_APPLY_ONLY);
            Object response = gateway.client().sOutputinvoice().fastRed(request, gateway.token());
            return BaiwangCodec.fastRedResult(response, orderNo);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云红冲申请");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云红冲申请");
        }
    }

    /** 查红字确认单状态：sRedconfirm().query。 */
    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        BaiwangGateway gateway = gateway(params);
        Invoice invoice = ctx.getInvoice();
        String confirmNo = invoice == null ? null : invoice.getRedApplyId();
        try {
            SRedconfirmQueryRequest request = BaiwangCodec.redConfirmQueryRequest(params, confirmNo);
            Object response = gateway.client().sRedconfirm().query(request, gateway.token());
            return BaiwangCodec.redConfirmResult(response, confirmNo);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云红冲状态查询");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云红冲状态查询");
        }
    }

    /** 第二段：快捷冲红 fastIssueRedType=1，确认后自动开具红票。 */
    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        BaiwangGateway gateway = gateway(params);
        Invoice invoice = ctx.requireInvoice();
        String orderNo = firstNonBlank(invoice.getBizOrderNo(), invoice.getChannelOrderNo());
        try {
            SOutputinvoiceFastRedRequest request = BaiwangCodec.fastRedRequest(params, invoice,
                    ctx.getRedApply(), BaiwangSpec.FAST_RED_AUTO_ISSUE);
            Object response = gateway.client().sOutputinvoice().fastRed(request, gateway.token());
            return BaiwangCodec.issueResult(response, orderNo);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云红字开具");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云红字开具");
        }
    }

    /** 快捷红冲：fastRed(type=1) 一次调用生成确认单并自动开具红票。 */
    @Override
    public QuickRedOutcome quickRed(InvoiceContext ctx) {
        return QuickRedOutcome.issued(issueRed(ctx));
    }

    /** 主动确认/否认红字确认单：sRedconfirm().operate。 */
    @Override
    public RedApplyResult confirm(InvoiceContext ctx, RedConfirmRequest request) {
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        BaiwangGateway gateway = gateway(params);
        String confirmNo = request == null || request.getRedApply() == null
                ? null : request.getRedApply().getRedConfirmNo();
        boolean accept = request != null && request.isAccept();
        String confirmType = accept ? BaiwangSpec.CONFIRM_TYPE_ACCEPT : BaiwangSpec.CONFIRM_TYPE_REJECT;
        try {
            SRedconfirmOperateRequest sdkRequest = BaiwangCodec.redConfirmOperateRequest(params,
                    confirmNo, confirmType);
            Object response = gateway.client().sRedconfirm().operate(sdkRequest, gateway.token());
            return BaiwangCodec.redOperateResult(response, confirmNo, accept);
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云确认单操作");
        } catch (RuntimeException e) {
            throw gateway.translate(e, "百望云确认单操作");
        }
    }

    // ==================== 回调 ====================

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        return BaiwangCallbackParser.parse(raw, config);
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        return BaiwangCallbackParser.reply(success, message);
    }

    // ==================== 交付 / 修复 ====================

    /** 交付：调用 sOutputinvoice().push 重新推送短信/邮箱。 */
    @Override
    public DeliveryResult resend(InvoiceContext ctx, DeliveryRequest request) {
        BaiwangParams params = params(ctx);
        requireCredentials(params);
        BaiwangGateway gateway = gateway(params);
        Invoice invoice = ctx.requireInvoice();
        DeliveryRequest req = request == null ? new DeliveryRequest() : request;
        String phone = firstNonBlank(req.getPhone(), invoice.getBuyerMobile());
        String email = firstNonBlank(req.getEmail(), invoice.getBuyerEmail());
        if (StringUtils.isBlank(invoice.getInvoiceNo())) {
            return DeliveryResult.failed("DELIVERY_NO_REQUIRED", "缺少发票号码，无法交付");
        }
        try {
            SOutputinvoicePushRequest pushRequest = BaiwangCodec.pushRequest(params, invoice, phone, email);
            Object response = gateway.client().sOutputinvoice().push(pushRequest, gateway.token());
            return BaiwangCodec.isSuccess(response)
                    ? DeliveryResult.ok()
                    : DeliveryResult.failed(nullTo(BaiwangCodec.errorCode(response), "DELIVERY_FAILED"),
                            nullTo(BaiwangCodec.errorMessage(response), "渠道交付失败"));
        } catch (com.baiwang.open.exception.BWOpenException e) {
            throw gateway.translate(e, "百望云交付");
        }
    }

    @Override
    public RepairResult apply(InvoiceContext ctx, RepairRequest request) {
        return RepairResult.failed("REPAIR_UNSUPPORTED", "百望云渠道暂未开放发票修复能力");
    }

    @Override
    public RepairResult query(InvoiceContext ctx, RepairRequest request) {
        return RepairResult.failed("REPAIR_UNSUPPORTED", "百望云渠道暂未开放发票修复能力");
    }

    // ==================== 内部工具 ====================

    private BaiwangGateway gateway(BaiwangParams params) {
        return new BaiwangGateway(params);
    }

    private BaiwangParams params(InvoiceContext ctx) {
        ChannelConfig config = ctx.getConfig();
        if (config != null && config.getParams() instanceof BaiwangParams) {
            return (BaiwangParams) config.getParams();
        }
        if (config != null) {
            return (BaiwangParams) parseParams(config.getAttrs());
        }
        throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "百望云渠道配置缺失");
    }

    private void requireCredentials(BaiwangParams params) {
        if (!params.hasCredentials()) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百望云缺少 appKey/appSecret/serverUrl，请在 attrs 中配置");
        }
        if (!params.hasTokenSource()) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百望云缺少 accessToken 或 username/password，无法获取 token");
        }
    }

    private void addAttrUrl(List<InvoiceFile> files, InvoiceFileType type,
                            Map<String, String> attrs, String... names) {
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
}
