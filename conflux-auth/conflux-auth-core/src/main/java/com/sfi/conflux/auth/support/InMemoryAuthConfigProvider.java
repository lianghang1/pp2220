package com.sfi.conflux.auth.support;

import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.exception.AuthErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录配置提供者内存实现：与支付域 {@code InMemoryChannelConfigProvider}、
 * 通知域 {@code InMemoryNotificationConfigProvider} 同构的注册入口。
 *
 * <p>主要供纯 Java / MVC 场景与单测使用；Spring Boot 场景由 starter 提供基于
 * 配置文件的实现。注册即覆盖（配置变更时重新 register 同一 configId+渠道即可）。</p>
 *
 * @author lianghang
 */
public class InMemoryAuthConfigProvider implements AuthConfigProvider {

    private final Map<String, AuthConfig> byConfig = new ConcurrentHashMap<String, AuthConfig>();

    /** 注册一份渠道配置（同 configId+channelCode 重复注册即覆盖）。 */
    public InMemoryAuthConfigProvider register(AuthConfig config) {
        if (config == null) {
            return this;
        }
        byConfig.put(key(config.getConfigId(), config.getChannelCode()), config);
        return this;
    }

    /** 按 configId + channelCode + 公共字段注册一份配置（平台特有键放 attr）。 */
    public InMemoryAuthConfigProvider register(String configId, String channelCode,
                                               String clientId, String clientSecret,
                                               String redirectUri, String scope,
                                               Map<String, String> attr) {
        return register(new AuthConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode)
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .setScope(scope)
                .setAttr(attr));
    }

    @Override
    public AuthConfig getConfig(String configId, String channelCode) {
        AuthConfig config = byConfig.get(key(configId, channelCode));
        if (config == null) {
            throw AuthErrorCode.CONFIG_MISSING.of(
                    "登录渠道配置缺失: configId=" + configId + ", channelCode=" + channelCode);
        }
        return config;
    }

    private static String key(String configId, String channelCode) {
        return configId + "#" + channelCode;
    }
}
