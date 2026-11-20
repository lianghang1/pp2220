package io.github.lianghang1.conflux.invoice.channels.piaotong;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.AuthChallenge;
import io.github.lianghang1.conflux.invoice.api.AuthResult;
import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.DeliveryRequest;
import io.github.lianghang1.conflux.invoice.api.DeliveryResult;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.QuickRedOutcome;
import io.github.lianghang1.conflux.invoice.api.RedApplyResult;
import io.github.lianghang1.conflux.invoice.api.RedConfirmRequest;
import io.github.lianghang1.conflux.invoice.channel.AuthSessionHandler;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceCallbackHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceDeliverer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceFileFetcher;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceRedHandler;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongCallbackParser;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongCodec;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongCodec.RedApplyParams;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongEnvelope;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongGateway;
import io.github.lianghang1.conflux.invoice.channels.piaotong.support.PiaoTongResponse;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 票通云渠道实现（以《票通数电发票接口文档 3.2.0》为准，与生产实现逐字段核对）。
 *
 * <p><b>协议特征</b>（与百旺金穗云完全不同）：POST JSON 明文信封；业务报文经
 * 3DES-ECB 加密（密钥 {@code password}）；签名 RSA {@code SHA1withRSA}，签名串为
 * 除 sign 外全部参数按 key 字典序 {@code k=v&k=v}；响应/回调须平台公钥验签后解密；
 * 接口路径后缀 {@code .pt}。</p>
 *
 * <p><b>异步语义</b>：开票受理后结果经 2.11 查询或 2.13/2.14 推送获得；
 * 状态码 0000 成功 / 6666 未开 / 7777 开票中 / 9999 失败 / 3999 需认证 /
 * 4999 红单申请中 / 5999 红单审核中。红字走两段流程（2.38 申请 → 2.29 查询 →
 * 必要时 2.31 审核 → 2.30 开具红票）。</p>
 *
 * <p><b>特定业务</b>：2.23~2.27 与 2.9 共用 {@code /invoiceBlue.pt} 端点，
 * 以附加字段（buildService/realEstateRentalList 等）区分；本适配器按核心模型
 * 国标字段自动附加对应节点，使用方无感。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PiaoTongChannelProvider implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceFileFetcher, InvoiceRedHandler,
        AuthSessionHandler, InvoiceCallbackHandler, InvoiceDeliverer {

    private static final long RED_CONFIRM_HOURS = 72L;

    @Override
    public String channelCode() {
        return PiaoTongSpec.CODE;
    }

    @Override
    public ChannelTier channelTier() {
        return ChannelTier.SERVICE_API;
    }

    @Override
    public Set<InvoiceType> supportedTypes() {
        return EnumSet.of(InvoiceType.DIGITAL_VAT_SPECIAL, InvoiceType.DIGITAL_VAT_GENERAL,
                InvoiceType.VAT_SPECIAL, InvoiceType.VAT_GENERAL);
    }

    // ==================== 配置解析 ====================

    @Override
    public ChannelConfig parseParams(Map<String, String> properties) {
        Map<String, String> props = properties == null
                ? Collections.<String, String>emptyMap() : properties;
        PiaoTongParams params = new PiaoTongParams()
                .setPlatformCode(first(props, "platformCode"))
                .setAliasName(first(props, "aliasName", "alias"))
                .setPassword(first(props, "password", "desKey"))
                .setPrivateKey(first(props, "privateKey", "rsaPrivateKey"))
                .setPublicKey(first(props, "publicKey", "rsaPublicKey", "platformPublicKey"))
                .setAccount(first(props, "account", "dlzh", "loginAccount"))
                .setSellerName(props.get("sellerName"))
                .setBaseUrl(firstNonBlank(props.get("baseUrl"), PiaoTongSpec.DEFAULT_BASE_URL))
                .setTimeoutMillis(parseLong(props.get("timeoutMillis"), 30000L));
        params.setSellerTaxNo(first(props, "sellerTaxNo", "taxpayerNum", "nsrsbh"));
        params.setAttr(new LinkedHashMap<String, String>(props));
        params.setChannelAppIdValue(firstNonBlank(
                first(props, "channelAppId", "platformCode"), params.getSellerTaxNo()));
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

    @Override
    public InvoiceDeliverer deliverer() {
        return this;
    }

    // ==================== 蓝字开票（2.9 / 2.23~2.27 同端点） ====================

    @Override
    public InvoiceResult issue(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String serial = requireSerial(invoice);
        Map<String, Object> body = PiaoTongCodec.issueBody(invoice,
                params.getSellerTaxNo(), serial);
        appendSpecialBiz(body, invoice);
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.ISSUE, body);
        log.debug("票通开票受理: serial={}, code={}", serial, response.getCode());
        if (!response.isAccepted()) {
            return InvoiceResult.failed(nullTo(response.getCode(), "PT_ISSUE_FAILED"),
                    response.getMessage());
        }
        // 受理即返回挂起；结果由 2.11 查询 / 2.13 推送推进
        InvoiceResult result = InvoiceResult.accepted(serial, true)
                .setRawPayload(response.getRawBody());
        String bizCode = response.bizText("code");
        if (bizCode != null) {
            // 部分部署同步即回业务结果，统一走同一归一逻辑
            return PiaoTongCodec.issueOrQueryResult(response, serial);
        }
        return result;
    }

    /**
     * 特定业务附加节点：建筑服务（tspz=03）→ {@code buildService} 对象；
     * 不动产经营租赁（tspz=06）→ {@code realEstateRentalList} 数组。
     * 字段键沿用票通文档命名，与百旺键的映射见 docs。
     */
    private void appendSpecialBiz(Map<String, Object> body, Invoice invoice) {
        Map<String, String> attr = invoice.getAttr();
        Map<String, String> jzfw = invoice.getJzfwTdys();
        if (jzfw != null && !jzfw.isEmpty()) {
            Map<String, Object> buildService = new LinkedHashMap<String, Object>();
            put(buildService, "landTaxItemNo", jzfw.get("landTaxItemNo"),
                    jzfw.get("tdzzsxmbh"));
            put(buildService, "buildServicePlace", jzfw.get("buildServicePlace"),
                    jzfw.get("jzfwfsd"));
            put(buildService, "detailedAddress", jzfw.get("detailedAddress"),
                    jzfw.get("fullAddress"));
            put(buildService, "buildProjectName", jzfw.get("buildProjectName"),
                    jzfw.get("jzxmmc"));
            put(buildService, "crossCitySign", jzfw.get("crossCitySign"),
                    jzfw.get("kdsbz"));
            put(buildService, "taxDeclareManageNum", jzfw.get("taxDeclareManageNum"),
                    jzfw.get("kqysssxbgglbm"));
            body.put("buildService", buildService);
        }
        List<Map<String, String>> bdcList = invoice.getBdcTdysList();
        if (bdcList != null && !bdcList.isEmpty()) {
            List<Map<String, Object>> rentalList = new ArrayList<Map<String, Object>>();
            for (Map<String, String> bdc : bdcList) {
                if (bdc == null) {
                    continue;
                }
                Map<String, Object> rental = new LinkedHashMap<String, Object>();
                put(rental, "region", bdc.get("region"), bdc.get("bdcdz"));
                put(rental, "detailedAddress", bdc.get("detailedAddress"),
                        bdc.get("fullAddress"));
                put(rental, "areaUnit", bdc.get("areaUnit"), bdc.get("mjdw"));
                put(rental, "crossCitySign", bdc.get("crossCitySign"), bdc.get("kdsbz"));
                put(rental, "leaseTerm", bdc.get("leaseTerm"), bdc.get("zlqqz"));
                put(rental, "certificateNumber", bdc.get("certificateNumber"),
                        bdc.get("cqzsh"));
                rentalList.add(rental);
            }
            body.put("realEstateRentalList", rentalList);
        }
        // 货物运输/旅客运输/不动产销售节点使用方较少，直接经 attr 原样透传
        putJsonAttr(body, attr, "goodsTransportList");
        putJsonAttr(body, attr, "passengerTransportList");
        putJsonAttr(body, attr, "realEstateSaleList");
        putJsonAttr(body, attr, "herBuyerList");
    }

    private void putJsonAttr(Map<String, Object> body, Map<String, String> attr, String key) {
        String json = attr == null ? null : attr.get(key);
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            body.put(key, JsonUtilsRead(json));
        } catch (Exception e) {
            log.warn("票通特定业务透传字段不是合法 JSON: key={}", key);
        }
    }

    private static Object JsonUtilsRead(String json) {
        return io.github.lianghang1.conflux.common.util.JsonUtils.readTree(json);
    }

    // ==================== 查单（2.11 主要信息） ====================

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String serial = firstNonBlank(invoice.getChannelOrderNo(),
                PiaoTongCodec.serialNo(invoice));
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.QUERY,
                PiaoTongCodec.queryBody(params.getSellerTaxNo(), serial));
        if (!response.isAccepted()) {
            // 查询失败不推翻在途单据，保持 PROCESSING 由推送/下次轮询推进
            return InvoiceResult.accepted(serial, true);
        }
        return PiaoTongCodec.issueOrQueryResult(response, serial);
    }

    // ==================== 版式文件（2.15） ====================

    /**
     * 2.15 获取数电发票文件：同步接口，返回 Base64 文件内容。
     * 文档明确「此接口比较耗时，请调长超时时间」。请求不到时返回空列表，
     * 由编排层的 XML 法定原件强约束兜底（SUCCEEDED 前必须归档 XML）。
     */
    @Override
    public List<InvoiceFile> fetch(InvoiceContext ctx, Set<InvoiceFileType> types) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        if (!hasCredentials(params)) {
            return Collections.emptyList();
        }
        Set<InvoiceFileType> wanted = types == null || types.isEmpty()
                ? EnumSet.of(InvoiceFileType.XML, InvoiceFileType.PDF) : types;
        String serial = firstNonBlank(invoice.getChannelOrderNo(),
                PiaoTongCodec.serialNo(invoice));
        String electronicNo = PiaoTongCodec.electronicNo(invoice);
        List<InvoiceFile> files = new ArrayList<InvoiceFile>();
        for (InvoiceFileType type : wanted) {
            String fileType = fileTypeOf(type);
            if (fileType == null) {
                continue;
            }
            try {
                PiaoTongResponse response = gateway(params).call(PiaoTongSpec.FILE,
                        PiaoTongCodec.fileBody(params.getSellerTaxNo(), serial,
                                electronicNo, fileType, invoice.getInvoiceDate()));
                String content = PiaoTongCodec.fileContent(response);
                if (StringUtils.isNotBlank(content)) {
                    files.add(new InvoiceFile().setType(type).setContent(content));
                }
            } catch (RuntimeException e) {
                log.warn("票通版式文件获取失败: type={}, serial={}, err={}",
                        fileType, serial, e.getMessage());
            }
        }
        return files;
    }

    private static String fileTypeOf(InvoiceFileType type) {
        switch (type) {
            case XML:
                return "XML";
            case PDF:
                return "PDF";
            case OFD:
                return "OFD";
            default:
                return null;
        }
    }

    // ==================== 两段红冲 ====================

    /**
     * 红字确认单申请：2.38 新版（支持全额/部分、销/购方申请）优先；
     * {@code attr.redApplyMode=OLD} 切回 2.28 旧版。
     *
     * <p>响应 {@code processState}：81 无需确认 / 84 双方已确认 → 直接 CONFIRMED；
     * 82/83 待对方确认 → WAITING_CONFIRM；4/85~90 失败或作废 → REJECTED。</p>
     */
    @Override
    public RedApplyResult apply(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        Map<String, String> invoiceAttr = invoice.getAttr();

        RedApplyParams p = new RedApplyParams();
        p.taxpayerNum = params.getSellerTaxNo();
        p.redApplySerialNo = firstNonBlank(
                apply == null ? null : apply.getChannelOrderNo(),
                PiaoTongEnvelope.invoiceSerialNo(params));
        p.redReason = redReason(apply, invoiceAttr);
        p.blueAllEleInvNo = PiaoTongCodec.electronicNo(invoice);
        p.invoiceCode = invoice.getInvoiceCode();
        p.invoiceNo = invoice.getInvoiceNo();
        p.blueInvoiceDate = firstNonBlank(
                first(invoiceAttr, "blueInvoiceDate"),
                invoice.getInvoiceDate() == null ? null
                        : invoice.getInvoiceDate().format(COMPACT_DATE));
        p.applySource = firstNonBlank(first(invoiceAttr, "applySource"), "0");
        p.invoiceKind = PiaoTongCodec.invoiceKind(invoice);
        p.account = firstNonBlank(params.getAccount(),
                first(invoiceAttr, "account"));

        boolean old = "OLD".equalsIgnoreCase(
                params.getAttr() == null ? null : params.getAttr().get("redApplyMode"));
        PiaoTongResponse response = gateway(params).call(
                old ? PiaoTongSpec.RED_APPLY_OLD : PiaoTongSpec.RED_APPLY_NEW,
                PiaoTongCodec.redApplyBody(invoice, p));
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_APPLY_FAILED"),
                    response.getMessage());
        }
        String state = PiaoTongCodec.processState(response);
        String redBillNo = response.bizText("redBillNo");
        RedApplyResult result = RedApplyResult.waiting(p.redApplySerialNo,
                LocalDateTime.now().plusHours(RED_CONFIRM_HOURS))
                .setRedConfirmNo(redBillNo)
                .setRawPayload(response.getRawBody());
        if (PiaoTongSpec.PS_READY_81.equals(state) || PiaoTongSpec.PS_READY_84.equals(state)) {
            result.setState(RedApplyState.CONFIRMED);
        } else if (PiaoTongCodec.isVoidState(state) || PiaoTongSpec.PS_FAILED_4.equals(state)) {
            result.setState(RedApplyState.REJECTED)
                    .setErrorMsg(response.bizText("statusMsg"));
        }
        return result;
    }

    /** 2.29 查看红字发票确认单（processState 全枚举）。 */
    @Override
    public RedApplyResult queryRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String redApplySerialNo = apply == null ? null : apply.getChannelOrderNo();
        if (StringUtils.isBlank(redApplySerialNo)) {
            return RedApplyResult.failed("RED_APPLY_NOT_FOUND", "红字申请缺少 redApplySerialNo");
        }
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.RED_VIEW,
                PiaoTongCodec.redViewBody(params.getSellerTaxNo(), redApplySerialNo));
        if (!response.isAccepted()) {
            return RedApplyResult.failed(nullTo(response.getCode(), "RED_QUERY_FAILED"),
                    response.getMessage());
        }
        return mapProcessState(response, redApplySerialNo,
                response.bizText("redBillNo"), response.bizText("statusMsg"));
    }

    /** 2.31 红字确认单审核（确认/拒绝，同步）。 */
    @Override
    public RedApplyResult confirm(InvoiceContext ctx, RedConfirmRequest request) {
        RedApply apply = ctx.getRedApply();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String redBillNo = apply == null ? null : apply.getRedConfirmNo();
        if (StringUtils.isBlank(redBillNo) && request != null && request.getExtra() != null) {
            redBillNo = request.getExtra().get("redBillNo");
        }
        if (StringUtils.isBlank(redBillNo)) {
            return RedApplyResult.failed("RED_CONFIRM_NO_REQUIRED",
                    "审核红字确认单缺少 redBillNo（红字信息表编号）");
        }
        boolean accept = request == null || request.isAccept();
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.RED_AUDIT,
                PiaoTongCodec.redAuditBody(params.getSellerTaxNo(), redBillNo, accept,
                        firstNonBlank(params.getAccount(),
                                request == null ? null
                                        : first(request.getExtra(), "account"))));
        RedApplyResult result = new RedApplyResult().setRedConfirmNo(redBillNo)
                .setRawPayload(response.getRawBody());
        String resultCode = response.bizText("resultCode");
        if (PiaoTongSpec.ST_SUCCESS.equals(resultCode)) {
            if (accept) {
                result.setState(RedApplyState.CONFIRMED);
            } else {
                result.setState(RedApplyState.REJECTED)
                        .setErrorMsg(firstNonBlank(request == null ? null : request.getReason(),
                                "我方拒绝红字确认单"));
            }
        } else {
            result.setState(RedApplyState.WAITING_CONFIRM)
                    .setConfirmDeadline(LocalDateTime.now().plusHours(RED_CONFIRM_HOURS))
                    .setErrorCode(nullTo(resultCode, "RED_CONFIRM_FAILED"))
                    .setErrorMsg(firstNonBlank(response.bizText("resultMsg"),
                            response.getMessage()));
        }
        return result;
    }

    /** 2.30 开具红字数电发票（异步受理，结果走 2.11 查询 / 2.13 推送）。 */
    @Override
    public InvoiceResult issueRed(InvoiceContext ctx) {
        RedApply apply = ctx.getRedApply();
        return doIssueRed(ctx, apply == null ? null : apply.getRedConfirmNo());
    }

    private InvoiceResult doIssueRed(InvoiceContext ctx, String redBillNo) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        if (StringUtils.isBlank(redBillNo)) {
            return InvoiceResult.failed("RED_BILL_NO_REQUIRED",
                    "开具红字发票缺少 redBillNo（红字信息表编号）");
        }
        String serial = PiaoTongEnvelope.invoiceSerialNo(params);
        Map<String, Object> body = PiaoTongCodec.redIssueBody(params.getSellerTaxNo(),
                serial, invoice, redBillNo, params.getAccount());
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.RED_ISSUE, body);
        if (!response.isAccepted()) {
            return InvoiceResult.failed(nullTo(response.getCode(), "RED_ISSUE_FAILED"),
                    firstNonBlank(response.getMessage(),
                            response.bizText("resultMsg")));
        }
        return InvoiceResult.accepted(serial, true).setRawPayload(response.getRawBody());
    }

    /** 快捷红冲：内部申请确认单，免确认的直接开具红票，需确认的返回确认单。 */
    @Override
    public QuickRedOutcome quickRed(InvoiceContext ctx) {
        RedApplyResult applied = apply(ctx);
        if (applied == null || applied.getState() == null) {
            return QuickRedOutcome.failed("QUICK_RED_APPLY_FAILED", "红字确认单申请未返回结果");
        }
        if (applied.getState() == RedApplyState.CONFIRMED) {
            return QuickRedOutcome.issued(doIssueRed(ctx, applied.getRedConfirmNo()), applied);
        }
        if (applied.getState() == RedApplyState.REJECTED || applied.getState() == RedApplyState.FAILED) {
            return QuickRedOutcome.failed(nullTo(applied.getErrorCode(), "QUICK_RED_APPLY_FAILED"),
                    applied.getErrorMsg());
        }
        return QuickRedOutcome.waiting(applied);
    }

    // ==================== 授权会话（2.4~2.8） ====================

    /**
     * 发起授权：先查 2.8 数电账号认证状态，按 {@code operationProposed} 分流：
     * 0 无需认证 → 直接通过；3 → 2.4 发短信验证码；1/2 → 2.6 取实名认证二维码。
     */
    @Override
    public AuthResult start(InvoiceContext ctx) {
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String account = accountOf(ctx, params);
        PiaoTongResponse status = gateway(params).call(PiaoTongSpec.AUTH_STATUS,
                PiaoTongCodec.accountBody(params.getSellerTaxNo(), account));
        String proposed = status.bizText("operationProposed");
        if (PiaoTongSpec.ST_SUCCESS.equals(status.bizText("resultCode"))
                && "0".equals(proposed)) {
            return AuthResult.verified(InvoiceState.SUBMITTING);
        }
        if ("3".equals(proposed)) {
            // 需短信认证：先请求发送验证码
            PiaoTongResponse sms = gateway(params).call(PiaoTongSpec.AUTH_SMS_CODE,
                    PiaoTongCodec.accountBody(params.getSellerTaxNo(), account));
            if (sms.isAccepted() && PiaoTongSpec.ST_SUCCESS.equals(sms.bizText("resultCode"))) {
                return AuthResult.pending(new AuthChallenge()
                        .setAuthType(AuthType.SMS)
                        .setChallengeText(firstNonBlank(sms.bizText("resultMsg"),
                                "短信验证码已发送，请提交验证码完成电子税局登录"))
                        .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
            }
            return AuthResult.rejected("AUTH_SMS_SEND_FAILED",
                    firstNonBlank(sms.bizText("resultMsg"), sms.getMessage()));
        }
        // 1/2 需扫码认证
        String qrcodeType = firstNonBlank(
                params.getAttr() == null ? null : params.getAttr().get("qrcodeType"), "1");
        PiaoTongResponse qr = gateway(params).call(PiaoTongSpec.AUTH_QRCODE,
                PiaoTongCodec.qrcodeBody(params.getSellerTaxNo(), account, qrcodeType));
        if (!PiaoTongSpec.ST_SUCCESS.equals(qr.bizText("resultCode"))) {
            return AuthResult.rejected("AUTH_QRCODE_FAILED",
                    firstNonBlank(qr.bizText("resultMsg"), qr.getMessage()));
        }
        Map<String, String> extra = new LinkedHashMap<String, String>();
        putStr(extra, "authId", qr.bizText("authId"));
        putStr(extra, "account", account);
        return AuthResult.pending(new AuthChallenge()
                .setAuthType(AuthType.FACE)
                .setChallengeUrl(qr.bizText("qrcodeContent", "qrcodeImg"))
                .setChallengeText("请使用税务 App 扫码完成实名认证")
                .setExtra(extra)
                .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
    }

    /**
     * 提交授权凭证：短信验证码走 2.5 登录；扫码 token 走 2.7 查询扫码状态
     * （authId 从挂起的 challenge extra 或本次提交 extra 取）。
     */
    @Override
    public AuthResult submit(InvoiceContext ctx, AuthSubmission submission) {
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String account = accountOf(ctx, params);
        Map<String, String> extra = submission == null ? null : submission.getExtra();
        if (submission != null && StringUtils.isNotBlank(submission.getCaptcha())) {
            PiaoTongResponse login = gateway(params).call(PiaoTongSpec.AUTH_SMS_LOGIN,
                    PiaoTongCodec.smsLoginBody(params.getSellerTaxNo(), account,
                            submission.getCaptcha()));
            if (PiaoTongSpec.ST_SUCCESS.equals(login.bizText("resultCode"))) {
                return AuthResult.verified(InvoiceState.SUBMITTING);
            }
            return AuthResult.rejected("AUTH_SMS_LOGIN_FAILED",
                    firstNonBlank(login.bizText("resultMsg"), login.getMessage()));
        }
        if (submission != null && StringUtils.isNotBlank(submission.getToken())) {
            String authId = firstNonBlank(submission.getToken(),
                    first(extra, "authId"));
            Map<String, Object> scanBody = PiaoTongCodec.accountBody(
                    params.getSellerTaxNo(), account);
            scanBody.put("authId", authId);
            PiaoTongResponse scan = gateway(params).call(PiaoTongSpec.AUTH_QRCODE_SCAN,
                    scanBody);
            String scanStatus = scan.bizText("scanStatus");
            if (!PiaoTongSpec.ST_SUCCESS.equals(scan.bizText("resultCode"))) {
                return AuthResult.rejected("AUTH_SCAN_QUERY_FAILED",
                        firstNonBlank(scan.bizText("resultMsg"), scan.getMessage()));
            }
            if ("2".equals(scanStatus)) {
                return AuthResult.verified(InvoiceState.SUBMITTING);
            }
            if ("1".equals(scanStatus)) {
                return AuthResult.pending(new AuthChallenge()
                        .setAuthType(AuthType.FACE)
                        .setChallengeText("二维码已扫码，等待认证结果")
                        .setExpireTime(LocalDateTime.now().plusMinutes(10L)));
            }
            return AuthResult.rejected("AUTH_QRCODE_EXPIRED",
                    firstNonBlank(scan.bizText("scanStatusMsg"), "二维码已过期，请重新发起授权"));
        }
        return AuthResult.rejected("AUTH_CREDENTIAL_MISSING", "短信验证码或扫码 token 不能为空");
    }

    // ==================== 回调 ====================

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        return PiaoTongCallbackParser.parse(raw, config);
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        return PiaoTongCallbackParser.reply(success, message);
    }

    // ==================== 发票交付（2.19） ====================

    @Override
    public DeliveryResult resend(InvoiceContext ctx, DeliveryRequest request) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        DeliveryRequest req = request == null ? new DeliveryRequest() : request;
        Map<String, String> extra = req.getExtra();
        String serial = firstNonBlank(invoice.getChannelOrderNo(),
                first(extra, "invoiceReqSerialNo"));
        String electronicNo = PiaoTongCodec.electronicNo(invoice);
        if (StringUtils.isBlank(serial) && StringUtils.isBlank(electronicNo)) {
            return DeliveryResult.failed("DELIVERY_NO_REQUIRED",
                    "缺少发票请求流水号或数电发票号码，无法重发交付");
        }
        Map<String, Object> body = PiaoTongCodec.resendBody(params.getSellerTaxNo(),
                serial, electronicNo,
                firstNonBlank(req.getPhone(), first(extra, "phone", "takerTel")),
                firstNonBlank(req.getEmail(), first(extra, "email", "takerEmail")));
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.RESEND, body);
        String resultCode = response.bizText("resultCode");
        boolean ok = response.isAccepted()
                && (resultCode == null || PiaoTongSpec.ST_SUCCESS.equals(resultCode));
        DeliveryResult result = ok
                ? DeliveryResult.ok()
                : DeliveryResult.failed(nullTo(resultCode, "DELIVERY_FAILED"),
                        firstNonBlank(response.bizText("resultMsg"), response.getMessage()));
        return result.setRawPayload(response.getRawBody());
    }

    // ==================== B 档便捷入口（框架不自动调用） ====================

    /**
     * 2.12 查询发票全票面信息（含明细 itemList）：主查询 2.11 只回主要信息，
     * 需要票面明细/购销方完整信息时调用。返回解密后的业务报文 JSON 树字符串。
     */
    public String queryFull(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String serial = firstNonBlank(invoice.getChannelOrderNo(),
                PiaoTongCodec.serialNo(invoice));
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.QUERY_FULL,
                PiaoTongCodec.queryBody(params.getSellerTaxNo(), serial));
        return response.getRoot() == null ? null : response.getRoot().toString();
    }

    /**
     * 2.33 从局端下载红字确认单 + 2.34 查询下载结果（组合）：
     * 先发起局端下载（queryType=1 数电确认单），再轮询 2.34 拿 redInfoTableList。
     * 返回 2.34 业务报文 JSON 树字符串（resultCode=0000 时含 redInfoTableList）。
     */
    public String queryRedFromBureau(InvoiceContext ctx, String dateStart, String dateEnd) {
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        String businessSerialNo = PiaoTongEnvelope.serialNo(params);
        gateway(params).call(PiaoTongSpec.RED_BUREAU, PiaoTongCodec.redBureauBody(
                params.getSellerTaxNo(), businessSerialNo, dateStart, dateEnd));
        PiaoTongResponse result = gateway(params).call(PiaoTongSpec.RED_RESULT,
                PiaoTongCodec.redResultBody(params.getSellerTaxNo(), businessSerialNo));
        return result.getRoot() == null ? null : result.getRoot().toString();
    }

    /** 2.45 查询数电账号列表（认证辅助）：返回业务报文 JSON 树字符串。 */
    public String listAccounts(InvoiceContext ctx) {
        PiaoTongParams params = params(ctx);
        requireCredentials(params);
        PiaoTongResponse response = gateway(params).call(PiaoTongSpec.ACCOUNT_LIST,
                PiaoTongCodec.accountBody(params.getSellerTaxNo(), params.getAccount()));
        return response.getRoot() == null ? null : response.getRoot().toString();
    }

    // ==================== 内部工具 ====================

    private PiaoTongGateway gateway(PiaoTongParams params) {
        return new PiaoTongGateway(params);
    }

    private PiaoTongParams params(InvoiceContext ctx) {
        ChannelConfig config = ctx.getConfig();
        if (config != null && config instanceof PiaoTongParams) {
            return (PiaoTongParams) config;
        }
        if (config != null) {
            return (PiaoTongParams) parseParams(config.getAttr());
        }
        throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "票通云渠道配置缺失");
    }

    private void requireCredentials(PiaoTongParams params) {
        if (!hasCredentials(params)) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "票通云缺少必要凭据（platformCode/aliasName/password/privateKey/"
                            + "publicKey/sellerTaxNo），请检查 conflux.invoice 配置");
        }
    }

    private boolean hasCredentials(PiaoTongParams params) {
        return params != null && params.hasCredentials();
    }

    /** 开票流水号：单据已记录（重试幂等）优先，否则新生成 20 位流水号。 */
    private String requireSerial(Invoice invoice) {
        if (StringUtils.isNotBlank(invoice.getChannelOrderNo())) {
            return invoice.getChannelOrderNo();
        }
        Map<String, String> attr = invoice.getAttr();
        if (attr != null && StringUtils.isNotBlank(attr.get("invoiceReqSerialNo"))) {
            return attr.get("invoiceReqSerialNo");
        }
        throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                "开票缺少 invoiceReqSerialNo：请通过 request.attr 预生成后传入");
    }

    private String accountOf(InvoiceContext ctx, PiaoTongParams params) {
        Invoice invoice = ctx.getInvoice();
        String account = firstNonBlank(params.getAccount(),
                invoice == null ? null : first(invoice.getAttr(), "account"));
        if (StringUtils.isBlank(account)) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "票通云授权链路需要电子税局登录账号（配置 account 或单据 attr.account）");
        }
        return account;
    }

    /** 冲红原因：票通 01/02/03/04；兼容百旺 chyydm 数字 1~4 自动补零。 */
    private String redReason(RedApply apply, Map<String, String> invoiceAttr) {
        String reason = firstNonBlank(
                invoiceAttr == null ? null : invoiceAttr.get("redReason"),
                apply == null ? null : apply.getChyydm());
        if (StringUtils.isBlank(reason)) {
            return PiaoTongSpec.DEFAULT_RED_REASON;
        }
        if (reason.length() == 1 && Character.isDigit(reason.charAt(0))) {
            return "0" + reason;
        }
        return reason;
    }

    private RedApplyResult mapProcessState(PiaoTongResponse response,
                                           String redApplySerialNo, String redBillNo,
                                           String statusMsg) {
        String state = PiaoTongCodec.processState(response);
        RedApplyResult result = new RedApplyResult()
                .setChannelOrderNo(redApplySerialNo)
                .setRedConfirmNo(firstNonBlank(redBillNo,
                        response.bizText("redInvConfirmationId")))
                .setRawPayload(response.getRawBody());
        if (PiaoTongSpec.PS_READY_81.equals(state) || PiaoTongSpec.PS_READY_84.equals(state)
                || PiaoTongSpec.PS_REDED.equals(state)) {
            result.setState(RedApplyState.CONFIRMED);
        } else if (PiaoTongCodec.isVoidState(state)) {
            result.setState(RedApplyState.REJECTED)
                    .setErrorMsg(firstNonBlank(statusMsg, "红字确认单已作废"));
        } else if (PiaoTongSpec.PS_FAILED_4.equals(state)
                || PiaoTongSpec.PS_FAILED_7.equals(state)
                || PiaoTongSpec.PS_FAILED_11.equals(state)) {
            result.setState(RedApplyState.FAILED)
                    .setErrorMsg(firstNonBlank(statusMsg, "红字确认单处理失败"));
        } else {
            result.setState(RedApplyState.WAITING_CONFIRM)
                    .setConfirmDeadline(LocalDateTime.now().plusHours(RED_CONFIRM_HOURS));
        }
        return result;
    }

    private static void put(Map<String, Object> body, String key, String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                body.put(key, value);
                return;
            }
        }
    }

    private static void putStr(Map<String, String> body, String key, String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                body.put(key, value);
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

    private static final DateTimeFormatter COMPACT_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");
}
