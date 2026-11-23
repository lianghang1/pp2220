package com.sfi.conflux.auth.autoconfigure;

import com.sfi.conflux.auth.channel.AuthChannel;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.engine.AuthGateway;
import com.sfi.conflux.auth.support.StateCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 登录网关自动装配：配置提供者 + 网关门面，与支付/短信 starter 同构。
 *
 * <p>默认提供基于配置文件的 {@link PropertiesAuthConfigProvider}（可被使用方
 * 同类型 Bean 覆盖，如 DB/配置中心实现）。内置渠道由 {@code AuthGateway.Builder.build()}
 * 经 SPI 清单自动全量注册，starter 不逐个声明；仅收集使用方声明的自定义
 * {@link AuthChannel} Bean。state 缓存走 SPI：使用方可注入 {@link StateCache} Bean
 * （如 Redis 分布式实现），未注入则门面用默认内存实现。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthAutoConfiguration {

    /** 默认配置提供者：读 conflux.auth.configs.*；使用方自定义同类型 Bean 可覆盖。 */
    @Bean
    @ConditionalOnMissingBean(AuthConfigProvider.class)
    public AuthConfigProvider authConfigProvider(AuthProperties properties) {
        return new PropertiesAuthConfigProvider(properties);
    }

    /**
     * 网关门面：注入配置提供者 + 可选 state 缓存；收集自定义渠道 Bean，
     * 内置渠道由 build 经 SPI 自动注册。与四域 starter 装配流水线同构。
     */
    @Bean
    @ConditionalOnMissingBean(AuthGateway.class)
    public AuthGateway authGateway(ObjectProvider<AuthConfigProvider> configProvider,
                                   ObjectProvider<StateCache> stateCache,
                                   List<AuthChannel> channels) {
        AuthConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 AuthConfigProvider Bean");
        }
        AuthGateway.Builder builder = AuthGateway.builder().configProvider(provider);
        StateCache cache = stateCache.getIfAvailable();
        if (cache != null) {
            builder.stateCache(cache);
        }
        if (channels != null) {
            for (AuthChannel channel : channels) {
                builder.channel(channel);
            }
        }
        AuthGateway gateway = builder.build();
        log.info("AuthGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
