package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.channel.ChannelRegistry;
import com.sfi.conflux.pay.config.ChannelConfigProvider;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 网关运行时上下文：各编排组件共享的依赖（package-private 传递）。
 *
 * <p>SDK 无状态——不持有任何台账，仅维护渠道路由、渠道配置与时钟；
 * 由 PaymentGateway.Builder 组装，构建后字段只读。</p>
 *
 * @author lianghang
 */
final class GatewayRuntime {

    final ChannelRegistry registry;
    final ChannelConfigProvider configProvider;
    final Clock clock;

    GatewayRuntime(ChannelRegistry registry, ChannelConfigProvider configProvider, Clock clock) {
        this.registry = registry;
        this.configProvider = configProvider;
        this.clock = clock;
    }

    /** 统一取当前时间（测试可注入固定 Clock）。 */
    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
