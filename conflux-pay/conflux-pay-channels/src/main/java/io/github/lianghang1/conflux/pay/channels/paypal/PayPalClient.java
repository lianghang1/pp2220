package io.github.lianghang1.conflux.pay.channels.paypal;

import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PayPal REST 客户端：client credentials 令牌自动获取与缓存、GET/POST JSON 执行。
 *
 * <p>令牌按应用凭据缓存，于过期前提前刷新；HTTP 传输统一走 common 的
 * {@link HttpTransport}，本类只保留 PayPal 的业务判定（2xx + 令牌解析）。
 * 全部方法线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PayPalClient {

    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;
    private static final long CONNECT_MILLIS = 10000L;
    private static final long READ_MILLIS = 30000L;
    private static final long WRITE_MILLIS = 10000L;

    private final ConcurrentHashMap<PayPalParams, CachedToken> tokens =
            new ConcurrentHashMap<PayPalParams, CachedToken>();

    /** 执行 PayPal REST 调用（自动携带令牌）；返回解析后的 JSON Map。 */
    public Map<String, Object> execute(String action, ChannelConfig config, String method,
                                       String path, Map<String, Object> requestBody) {
        PayPalParams params = PayPalClient.paramsOf(config);
        try {
            String url = params.gateway(config.isSandbox()) + path;
            Map<String, String> headers = Collections.singletonMap(
                    "Authorization", "Bearer " + accessToken(config, params));
            byte[] body = requestBody == null ? new byte[0] : JsonUtils.toJsonBytes(requestBody);
            HttpResponse response = HttpTransport.execute(method, url, headers,
                    HttpTransport.JSON, body, CONNECT_MILLIS, READ_MILLIS, WRITE_MILLIS);
            String text = response.getBody();
            if (!response.isSuccessful()) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "PayPal{}失败: status={}, body={}", action, response.getStatusCode(), text);
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

    /** 获取有效令牌（缓存未过期直接复用，否则刷新）。 */
    private String accessToken(ChannelConfig config, PayPalParams params) {
        CachedToken cached = tokens.get(params);
        if (cached != null && cached.validBefore.isAfter(LocalDateTime.now().plusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
            return cached.token;
        }
        synchronized (tokens) {
            cached = tokens.get(params);
            if (cached != null && cached.validBefore.isAfter(LocalDateTime.now().plusSeconds(TOKEN_REFRESH_MARGIN_SECONDS))) {
                return cached.token;
            }
            CachedToken fresh = requestToken(config, params);
            tokens.put(params, fresh);
            return fresh.token;
        }
    }

    private CachedToken requestToken(ChannelConfig config, PayPalParams params) {
        String credentials = Base64.getEncoder().encodeToString(
                (params.getClientId() + ":" + params.getClientSecret())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            String url = params.gateway(config.isSandbox()) + "/v1/oauth2/token";
            Map<String, String> headers = Collections.singletonMap("Authorization", "Basic " + credentials);
            HttpResponse response = HttpTransport.postForm(url, headers,
                    "grant_type=client_credentials", CONNECT_MILLIS, READ_MILLIS, WRITE_MILLIS);
            String text = response.getBody();
            if (!response.isSuccessful()) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "PayPal 令牌获取失败: status={}, body={}", response.getStatusCode(), text);
            }
            Map<String, Object> token = JsonUtils.toMap(text);
            long expiresSeconds = token.get("expires_in") == null ? 3600
                    : Long.parseLong(String.valueOf(token.get("expires_in")));
            String accessToken = String.valueOf(token.get("access_token"));
            log.info("PayPal 令牌已刷新: clientId={}, expiresIn={}s", params.getClientId(), expiresSeconds);
            return new CachedToken(accessToken, LocalDateTime.now().plusSeconds(expiresSeconds));
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
