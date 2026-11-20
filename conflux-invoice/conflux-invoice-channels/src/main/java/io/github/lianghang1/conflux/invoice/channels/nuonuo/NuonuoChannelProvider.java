package io.github.lianghang1.conflux.invoice.channels.nuonuo;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackEvent;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackReply;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceCallbackHandler;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.support.NuonuoCallbackParser;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.support.NuonuoCodec;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.support.NuonuoGateway;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 诺诺开放平台渠道实现。
 *
 * <p>基于官方 SDK（{@code com.nuonuo:open-sdk}）：签名/报文组装/HTTP 全由
 * {@code NNOpenSDK} 完成，渠道侧只组装业务 JSON 并解析响应，不自行实现签名。
 * 目前实现文档已核对的能力：蓝字开票、发票结果查询、开票结果回调；
 * 红冲/交付/授权/修复等本次未核对到足够字段，按项目"不臆造"原则不暴露。</p>
 *
 * <p>无状态、线程安全，所有运行态从 {@link InvoiceContext} 取得。</p>
 *
 * @author lianghang
 */
@Slf4j
public class NuonuoChannelProvider implements InvoiceChannelProvider,
        InvoiceIssuer, InvoiceQuerier, InvoiceCallbackHandler {

    @Override
    public String channelCode() {
        return NuonuoSpec.CODE;
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
        boolean sandbox = Boolean.parseBoolean(props.get("sandbox"));
        NuonuoParams params = new NuonuoParams()
                .setAppKey(first(props, "appKey", "clientId"))
                .setAppSecret(first(props, "appSecret", "clientSecret"))
                .setAccessToken(first(props, "accessToken", "token"))
                .setSellerName(props.get("sellerName"))
                .setDrawer(first(props, "drawer", "clerk", "kpr"))
                .setSellerPhone(props.get("sellerPhone"))
                .setSellerAddress(props.get("sellerAddress"))
                .setSellerBankAccount(first(props, "sellerBankAccount", "salerAccount"))
                .setCallbackUrl(first(props, "callbackUrl", "callBackUrl", "notifyUrl"))
                .setGateway(firstNonBlank(props.get("gateway"), props.get("gatewayUrl"),
                        props.get("baseUrl"), sandbox
                                ? NuonuoSpec.SANDBOX_GATEWAY : NuonuoSpec.DEFAULT_GATEWAY))
                .setTokenJsonPath(props.get("tokenJsonPath"))
                .setTimeoutMillis(parseInt(props.get("timeoutMillis"), 30000));
        params.setSellerTaxNo(first(props, "sellerTaxNo", "salerTaxNum", "taxnum", "nsrsbh"));
        params.setSandbox(sandbox);
        params.setAttr(new LinkedHashMap<String, String>(props));
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
    public InvoiceCallbackHandler callbackHandler() {
        return this;
    }

    // ==================== 蓝字开票 ====================

    @Override
    public InvoiceResult issue(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        NuonuoParams params = params(ctx);
        requireCredentials(params);
        String orderNo = firstNonBlank(invoice.getBizOrderNo(), invoice.getChannelOrderNo());
        String callbackUrl = firstNonBlank(invoice.getNotifyUrl(), params.getCallbackUrl());
        NuonuoGateway gateway = gateway(params);
        String content = NuonuoCodec.issueContent(ctx, params, orderNo, callbackUrl);
        String response = gateway.call(NuonuoSpec.METHOD_REQUEST_BILLING, content);
        log.debug("诺诺开票受理: orderNo={}", orderNo);
        return NuonuoCodec.issueResult(response, orderNo);
    }

    // ==================== 查单 ====================

    @Override
    public InvoiceResult query(InvoiceContext ctx) {
        Invoice invoice = ctx.requireInvoice();
        NuonuoParams params = params(ctx);
        requireCredentials(params);
        String serialNo = invoice.getChannelOrderNo();
        String orderNo = invoice.getBizOrderNo();
        NuonuoGateway gateway = gateway(params);
        String content = NuonuoCodec.queryContent(serialNo, orderNo);
        String response = gateway.call(NuonuoSpec.METHOD_QUERY_INVOICE, content);
        return NuonuoCodec.queryResult(response, serialNo, orderNo);
    }

    // ==================== 回调 ====================

    @Override
    public InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config) {
        return NuonuoCallbackParser.parse(raw, config);
    }

    @Override
    public InvoiceCallbackReply reply(boolean success, String message) {
        return NuonuoCallbackParser.reply(success, message);
    }

    // ==================== 内部工具 ====================

    private NuonuoGateway gateway(NuonuoParams params) {
        return new NuonuoGateway(params);
    }

    private NuonuoParams params(InvoiceContext ctx) {
        ChannelConfig config = ctx.getConfig();
        if (config != null && config instanceof NuonuoParams) {
            return (NuonuoParams) config;
        }
        if (config != null) {
            return (NuonuoParams) parseParams(config.getAttr());
        }
        throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "诺诺渠道配置缺失");
    }

    private void requireCredentials(NuonuoParams params) {
        if (!params.hasCredentials()) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "诺诺缺少 appKey/appSecret，请在 attr 中配置");
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

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
