package com.sfi.conflux.invoice.channels.baiwang.support;

import com.baiwang.open.client.BWRestClient;
import com.baiwang.open.client.IBWClient;
import com.baiwang.open.client.login.BopLoginResponse;
import com.baiwang.open.client.login.LoginResult;
import com.baiwang.open.client.login.PasswordLoginClient;
import com.baiwang.open.client.login.PasswordLoginConfig;
import com.baiwang.open.exception.BWOpenException;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.invoice.channels.baiwang.BaiwangParams;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 百望云 SDK 网关：封装官方 {@link BWRestClient} 的初始化、token 登录与异常翻译。
 *
 * <p><b>token 策略</b>：百望开放平台所有业务接口都要求 token（appSecret 只用于签名）。
 * 优先用配置的 {@code accessToken}；否则按 username/password/userSalt 走
 * {@link PasswordLoginClient} 登录获取，并按 {@code appKey|username} 缓存复用，
 * token 过期（接口返回 token 相关错误）时主动失效重登一次。</p>
 *
 * <p><b>边界</b>：本类只做 SDK 调用与异常翻译，不解析业务字段（归 Codec），
 * 把 SDK 的 {@link BWOpenException} 统一翻成框架 {@link GatewayException}。</p>
 *
 * @author lianghang
 */
@Slf4j
public final class BaiwangGateway {

    /** token 缓存：key = appKey|username，value = accessToken。 */
    private static final ConcurrentMap<String, String> TOKEN_CACHE = new ConcurrentHashMap<String, String>();

    private final BaiwangParams params;
    private final IBWClient client;

    public BaiwangGateway(BaiwangParams params) {
        if (params == null || !params.hasCredentials()) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百望云缺少 appKey/appSecret/serverUrl 配置");
        }
        this.params = params;
        try {
            this.client = new BWRestClient(params.getServerUrl(), params.getAppKey(), params.getAppSecret());
        } catch (RuntimeException e) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百望云 SDK 客户端初始化失败: " + e.getMessage(), e);
        }
    }

    /** 当前参数对应的 SDK 客户端（供 Codec 调用各接口组）。 */
    public IBWClient client() {
        return client;
    }

    /** 获取业务接口 token（先缓存/配置，后登录）。 */
    public String token() {
        String configured = params.getAccessToken();
        if (configured != null && configured.trim().length() > 0) {
            return configured;
        }
        String key = cacheKey();
        String cached = TOKEN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (TOKEN_CACHE) {
            cached = TOKEN_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            String fresh = login();
            TOKEN_CACHE.put(key, fresh);
            return fresh;
        }
    }

    /** token 失效：清缓存，下次 token() 重新登录。 */
    public void invalidateToken() {
        TOKEN_CACHE.remove(cacheKey());
    }

    /** 按用户名密码登录获取 accessToken。 */
    private String login() {
        if (!params.hasTokenSource() || params.getUsername() == null || params.getPassword() == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "百望云缺少 username/password（或 accessToken），无法获取 token");
        }
        try {
            PasswordLoginConfig cfg = new PasswordLoginConfig();
            cfg.setUrl(params.getServerUrl());
            cfg.setClientId(params.getAppKey());
            cfg.setClientSecret(params.getAppSecret());
            cfg.setUsername(params.getUsername());
            cfg.setPassword(params.getPassword());
            if (params.getUserSalt() != null) {
                cfg.setUserSalt(params.getUserSalt());
            }
            PasswordLoginClient loginClient = new PasswordLoginClient(cfg);
            BopLoginResponse response = loginClient.login();
            LoginResult result = response == null ? null : response.getResponse();
            String token = result == null ? null : result.getAccessToken();
            if (token == null || token.trim().length() == 0) {
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "百望云登录未返回 accessToken");
            }
            log.debug("百望云登录成功: appKey={}, username={}", params.getAppKey(), params.getUsername());
            return token;
        } catch (BWOpenException e) {
            throw translate(e, "百望云登录");
        }
    }

    /** 把 SDK 异常翻译为框架异常。 */
    public GatewayException translate(BWOpenException e, String action) {
        String code = e.getCode();
        String message = e.getMessage();
        String sub = e.getSubCode();
        // token 相关错误：清缓存以便下次重登
        if (code != null && (code.contains("token") || "100002".equals(code)
                || (sub != null && sub.toLowerCase().contains("token")))) {
            invalidateToken();
        }
        return new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                "{}失败: code={}, subCode={}, message={}", action, code, sub, message, e);
    }

    /** 把通用运行时异常翻译为框架异常。 */
    public GatewayException translate(RuntimeException e, String action) {
        if (e instanceof GatewayException) {
            return (GatewayException) e;
        }
        return new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                "{}失败: {}", action, e.getMessage(), e);
    }

    private String cacheKey() {
        return params.getAppKey() + "|" + params.getUsername();
    }
}
