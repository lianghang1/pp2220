package com.sfi.conflux.invoice.engine;

import com.sfi.conflux.common.exception.GatewayAssert;
import com.sfi.conflux.invoice.channel.InvoiceChannelRegistry;
import com.sfi.conflux.invoice.config.InvoiceConfigProvider;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 发票网关运行时上下文：各编排组件共享的依赖。
 *
 * <p>SDK 无状态——不持有任何台账、授权会话或版式文件库，仅维护渠道路由、
 * 渠道配置与时钟。由 InvoiceGateway 组装，构建后字段只读。</p>
 *
 * @author lianghang
 */
final class InvoiceRuntime {

    final InvoiceChannelRegistry registry;
    final InvoiceConfigProvider configProvider;
    final Clock clock;

    InvoiceRuntime(InvoiceChannelRegistry registry, InvoiceConfigProvider configProvider, Clock clock) {
        this.registry = GatewayAssert.notNull(registry, InvoiceErrorCode.CHANNEL_NOT_FOUND,
                "InvoiceChannelRegistry 未注册");
        this.configProvider = GatewayAssert.notNull(configProvider, InvoiceErrorCode.CONFIG_MISSING,
                "InvoiceConfigProvider 未注册");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    /** 统一取当前时间（测试可注入固定 Clock）。 */
    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
