package com.sfi.conflux.auth.engine;

import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.channel.AbstractAuthChannel;
import com.sfi.conflux.auth.channel.AuthChannel;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.exception.AuthErrorCode;
import com.sfi.conflux.auth.spi.BuiltinAuthChannels;
import com.sfi.conflux.auth.support.InMemoryStateCache;
import com.sfi.conflux.auth.support.StateCache;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 聚合登录网关门面：统一登录 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p><b>门面只做路由，不做任何拦截</b>：{@link #authorize} / {@link #parseCallback}
 * 内部只有「按 configId+channelCode 查配置 → 找到 channel → 转发」三步，没有 state 生成、
 * 校验、configId 比对、验签。state 链路在 {@link AbstractAuthChannel} 闭环，验签与身份
 * 交换由 provider 委托底层 SDK 完成。</p>
 *
 * <p>与四域对齐的对外契约：{@link Builder#configProvider} 注入配置源，
 * {@link #registerChannel} 声明渠道，{@link #registeredChannels} 查看已声明渠道，
 * {@link Builder#build()} 自动 SPI 注册内置渠道、{@link Builder#channel} 声明自定义渠道。</p>
 *
 * @author lianghang
 */
public class AuthGateway {

    private final Map<String, AuthChannel> channels;
    private final AuthConfigProvider configProvider;

    private AuthGateway(Builder builder) {
        this.channels = builder.channels;
        this.configProvider = builder.configProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 生成授权入口：网页返回 URL、扫码返回二维码、小程序/APP 返回 JSAPI 参数 */
    public AuthorizeResult authorize(String configId, String channelCode) {
        return authorize(configId, channelCode, null);
    }

    /** 带自定义参数（扫码样式、scope 覆盖、业务透传 state 上下文等） */
    public AuthorizeResult authorize(String configId, String channelCode, AuthorizeOption option) {
        AuthChannel channel = requireChannel(channelCode);
        AuthConfig config = configProvider.get(configId, channelCode);
        if (config == null) {
            throw AuthErrorCode.CONFIG_MISSING.of("未找到配置: configId=" + configId + ", channelCode=" + channelCode);
        }
        return channel.authorize(config, option);
    }

    /** 回调换身份：显式传 configId + channelCode 定位，门面只转发 */
    public AuthResult<AuthUser> parseCallback(String configId, String channelCode, AuthCallbackRequest request) {
        AuthChannel channel = requireChannel(channelCode);
        AuthConfig config = configProvider.get(configId, channelCode);
        if (config == null) {
            return AuthResult.fail("CONFIG_MISSING", "未找到配置: configId=" + configId + ", channelCode=" + channelCode);
        }
        return channel.exchange(config, request);
    }

    /** 声明渠道（与四域 registerChannel 对齐） */
    public void registerChannel(AuthChannel channel) {
        if (channel == null || channel.channelCode() == null) {
            throw AuthErrorCode.BAD_REQUEST.of("渠道或 channelCode 不能为空");
        }
        channels.put(channel.channelCode(), channel);
    }

    /** 已声明渠道编码集合（对齐四域 registeredChannels） */
    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(channels.keySet()));
    }

    private AuthChannel requireChannel(String channelCode) {
        AuthChannel channel = channels.get(channelCode);
        if (channel == null) {
            throw AuthErrorCode.CHANNEL_NOT_FOUND.of("未注册渠道: " + channelCode);
        }
        return channel;
    }

    /** 网关构建器：持有渠道表、配置源与 state 缓存，零 Spring 依赖。 */
    public static class Builder {

        private final java.util.concurrent.ConcurrentHashMap<String, AuthChannel> channels =
                new java.util.concurrent.ConcurrentHashMap<String, AuthChannel>();
        private AuthConfigProvider configProvider;
        private StateCache stateCache;

        /** 声明自定义渠道，可多次调用。 */
        public Builder channel(AuthChannel channel) {
            if (channel == null || channel.channelCode() == null) {
                throw AuthErrorCode.BAD_REQUEST.of("渠道或 channelCode 不能为空");
            }
            channels.put(channel.channelCode(), channel);
            return this;
        }

        /** 渠道配置提供者（必填，对齐四域同名方法） */
        public Builder configProvider(AuthConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** state 缓存 SPI（可选，默认内存实现） */
        public Builder stateCache(StateCache stateCache) {
            this.stateCache = stateCache;
            return this;
        }

        /** 构建：配置源必填；为所有继承 AbstractAuthChannel 的渠道注入 state 缓存；SPI 自动注册内置渠道。 */
        public AuthGateway build() {
            if (configProvider == null) {
                throw AuthErrorCode.CONFIG_MISSING.of("未设置渠道配置提供者");
            }
            final StateCache cache = stateCache == null ? new InMemoryStateCache() : stateCache;
            for (AuthChannel ch : channels.values()) {
                if (ch instanceof AbstractAuthChannel) {
                    ((AbstractAuthChannel) ch).setStateCache(cache);
                }
            }
            // SPI 自动注册内置渠道（详见 spi.BuiltinAuthChannelRegistrar）：
            // 由渠道模块提供实现，build() 时统一发现并注入 state 缓存。
            for (AuthChannel builtin : BuiltinAuthChannels.all()) {
                if (builtin instanceof AbstractAuthChannel) {
                    ((AbstractAuthChannel) builtin).setStateCache(cache);
                }
                channels.put(builtin.channelCode(), builtin);
            }
            return new AuthGateway(this);
        }
    }
}
