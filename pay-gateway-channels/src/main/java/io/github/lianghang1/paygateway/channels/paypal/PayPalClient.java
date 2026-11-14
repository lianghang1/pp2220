package io.github.lianghang1.paygateway.channels.paypal;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * PayPal REST 客户端：client credentials 令牌自动获取与缓存、GET/POST JSON 执行。
 *
 * <p>令牌按应用凭据缓存，于过期前提前刷新；HTTP 非 2xx 收敛为网关渠道异常
 * 并保留 PayPal 的 name/message 错误信息。全部方法线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PayPalClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final ConcurrentHashMap<PayPalParams, CachedToken> tokens =
            new ConcurrentHashMap<PayPalParams, CachedToken>();

    /** 执行 PayPal REST 调用（自动携带令牌）；返回解析后的 JSON Map。 */
    public Map<String, Object> execute(String action, ChannelConfig config, String method,
                                       String path, Map<String, Object> requestBody) {
        PayPalParams params = PayPalClient.paramsOf(config);
        try (Response response = client.newCall(buildRequest(params, method, path, requestBody)).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "PayPal{}失败: status={}, body={}", action, response.code(), text);
            }
            log.info("PayPal{}完成: {} {}", action, method, path);
            return JsonUtils.toMap(text);
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "PayPal{}调用失败: {}", action, e.getMessage(), e);
        }
    }

    private Request buildRequest(PayPalParams params, String method, String path, Map<String, Object> requestBody) {
        Request.Builder builder = new Request.Builder()
                .url(params.gateway() + path)
                .header("Authorization", "Bearer " + accessToken(params));
        boolean hasBody = requestBody != null;
        switch (method) {
            case "POST":
                builder.post(RequestBody.create(hasBody ? JsonUtils.toJsonBytes(requestBody)
                        : new byte[0], JSON));
                break;
            case "GET":
                builder.get();
                break;
            default:
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "不支持的 HTTP 方法: {}", method);
        }
        return builder.build();
    }

    /** 获取有效令牌（缓存未过期直接复用，否则刷新）。 */
    private String accessToken(PayPalParams params) {
        CachedToken cached = tokens.get(params);
        if (cached != null && cached.validBefore.isAfter(LocalDateTime.now().plusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
            return cached.token;
        }
        synchronized (tokens) {
            cached = tokens.get(params);
            if (cached != null && cached.validBefore.isAfter(LocalDateTime.now().plusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
                return cached.token;
            }
            CachedToken fresh = requestToken(params);
            tokens.put(params, fresh);
            return fresh.token;
        }
    }

    private CachedToken requestToken(PayPalParams params) {
        String credentials = Base64.getEncoder().encodeToString(
                (params.getClientId() + ":" + params.getClientSecret()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Request request = new Request.Builder()
                .url(params.gateway() + "/v1/oauth2/token")
                .header("Authorization", "Basic " + credentials)
                .post(RequestBody.create("grant_type=client_credentials",
                        MediaType.parse("application/x-www-form-urlencoded")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "PayPal 令牌获取失败: status={}, body={}", response.code(), text);
            }
            Map<String, Object> token = JsonUtils.toMap(text);
            long expiresSeconds = token.get("expires_in") == null ? 3600
                    : Long.parseLong(String.valueOf(token.get("expires_in")));
            String accessToken = String.valueOf(token.get("access_token"));
            log.info("PayPal 令牌已刷新: clientId={}, expiresIn={}s", params.getClientId(), expiresSeconds);
            return new CachedToken(accessToken,
                    LocalDateTime.now().plusSeconds(expiresSeconds));
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "PayPal 令牌获取异常: {}", e.getMessage(), e);
        }
    }

    public static PayPalParams paramsOf(ChannelConfig config) {
        if (config.getParams() instanceof PayPalParams) {
            return (PayPalParams) config.getParams();
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "PayPal 渠道参数类型不匹配: {}", config.getParams() == null ? "null" : config.getParams().getClass().getName());
    }

    /** 缓存的访问令牌。 */
    private static class CachedToken {
        private final String token;
        private final LocalDateTime validBefore;

        private CachedToken(String token, LocalDateTime validBefore) {
            this.token = token;
            this.validBefore = validBefore;
        }
    }
}
