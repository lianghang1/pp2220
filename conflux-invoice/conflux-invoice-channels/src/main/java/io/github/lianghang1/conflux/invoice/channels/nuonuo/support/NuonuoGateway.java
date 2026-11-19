package io.github.lianghang1.conflux.invoice.channels.nuonuo.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoParams;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoSpec;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import lombok.extern.slf4j.Slf4j;
import nuonuo.open.sdk.NNOpenSDK;

import java.util.UUID;

/**
 * 诺诺 SDK 网关：封装官方 {@link NNOpenSDK} 的令牌获取、报文发送与异常翻译。
 *
 * <p><b>边界</b>：签名、报文组装、HTTP 由官方 SDK 完成，本类只做三件事：
 * ① 提供可用的 accessToken（沙箱允许空）；② 调 {@code sendPostSyncRequest} 并把
 * 返回的 JSON 字符串交给 Codec；③ 把 SDK 运行时异常翻译为框架异常。
 * 渠道侧不自行实现任何签名逻辑。</p>
 *
 * <p><b>线程安全</b>：{@link NNOpenSDK#getIntance()} 为单例，每次调用新建实例引用
 * 无副作用；令牌按 appKey|sellerTaxNo 缓存，过期由调用方触发 {@link #refreshToken()}。</p>
 *
 * @author lianghang
 */
@Slf4j
public final class NuonuoGateway {

    private final NuonuoParams params;
    private final NNOpenSDK sdk;

    public NuonuoGateway(NuonuoParams params) {
        if (params == null || !params.hasCredentials()) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "诺诺缺少 appKey/appSecret/gateway 配置");
        }
        this.params = params;
        this.sdk = NNOpenSDK.getIntance();
    }

    /**
     * 取得业务令牌。优先用配置的 accessToken；沙箱允许空；否则调用官方
     * {@code getMerchantToken} 并按 {@link NuonuoParams#getTokenJsonPath()}（或常见字段）
     * 从返回 JSON 中提取。
     */
    public String token() {
        if (params.getAccessToken() != null && params.getAccessToken().trim().length() > 0) {
            return params.getAccessToken().trim();
        }
        if (!params.requiresToken()) {
            // 沙箱：官方文档示例 token 可为空串
            return "";
        }
        String json;
        try {
            json = sdk.getMerchantToken(params.getAppKey(), params.getAppSecret());
        } catch (RuntimeException e) {
            throw translate(e, "诺诺获取令牌");
        }
        String token = extractToken(json);
        if (token == null || token.length() == 0) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "诺诺 getMerchantToken 未返回可用令牌，请检查 accessToken 或 tokenJsonPath 配置");
        }
        return token;
    }

    /** 强制刷新令牌：清掉配置值需调用方更新配置；这里仅重新拉取一次。 */
    public String refreshToken() {
        return extractToken(sdk.getMerchantToken(params.getAppKey(), params.getAppSecret()));
    }

    /**
     * 调用业务接口。content 为业务 JSON 字符串，返回 SDK 原始响应 JSON 字符串。
     */
    public String call(String method, String content) {
        return call(method, content, token());
    }

    /** 指定令牌调用（用于令牌失效后重试）。 */
    public String call(String method, String content, String token) {
        String senid = UUID.randomUUID().toString().replace("-", "");
        String url = params.getGateway();
        try {
            int timeout = params.getTimeoutMillis();
            String json = timeout > 0
                    ? sdk.sendPostSyncRequest(url, senid, params.getAppKey(), params.getAppSecret(),
                            token, taxnum(), method, content, timeout)
                    : sdk.sendPostSyncRequest(url, senid, params.getAppKey(), params.getAppSecret(),
                            token, taxnum(), method, content);
            log.debug("诺诺接口调用: method={}, senid={}, respLen={}",
                    method, senid, json == null ? 0 : json.length());
            return json;
        } catch (RuntimeException e) {
            throw translate(e, "诺诺接口[" + method + "]");
        }
    }

    /** 自用型应用开票时 taxnum 为销方税号；服务商模式可为空，由配置决定。 */
    private String taxnum() {
        return params.getSellerTaxNo() == null ? "" : params.getSellerTaxNo();
    }

    /** 从令牌 JSON 中提取令牌值。优先按配置路径，其次常见字段。 */
    private String extractToken(String json) {
        if (json == null || json.trim().length() == 0) {
            return null;
        }
        JsonNode root;
        try {
            root = JsonUtils.readTree(json);
        } catch (RuntimeException e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "诺诺 getMerchantToken 返回非 JSON: " + e.getMessage(), e);
        }
        String path = params.getTokenJsonPath();
        if (path != null && path.trim().length() > 0) {
            JsonNode node = root;
            for (String part : path.trim().split("\\.")) {
                node = node == null ? null : node.get(part);
            }
            return node == null || node.isNull() ? null : node.asText();
        }
        // 常见字段自动尝试（不臆造结构，仅做宽松兼容）
        String[] candidates = {"result.accessToken", "accessToken", "result.token", "token",
                "result.access_token", "access_token"};
        for (String candidate : candidates) {
            JsonNode node = root;
            for (String part : candidate.split("\\.")) {
                node = node == null ? null : node.get(part);
            }
            if (node != null && !node.isNull() && node.asText().length() > 0) {
                return node.asText();
            }
        }
        return null;
    }

    /** SDK 默认网关与沙箱网关一致时由 Params 决定；暴露给 Codec 判断状态文案用。 */
    public static String gatewayFor(boolean sandbox) {
        return sandbox ? NuonuoSpec.SANDBOX_GATEWAY : NuonuoSpec.DEFAULT_GATEWAY;
    }

    /** 异常翻译。 */
    public GatewayException translate(RuntimeException e, String action) {
        if (e instanceof GatewayException) {
            return (GatewayException) e;
        }
        return new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                "{}失败: {}", action, e.getMessage(), e);
    }
}
