package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.channel.ChannelRegistry;
import io.github.lianghang1.paygateway.config.ChannelConfigProvider;
import io.github.lianghang1.paygateway.event.PaymentEventListener;
import io.github.lianghang1.paygateway.store.NotifyLogStore;
import io.github.lianghang1.paygateway.store.PaymentStore;
import io.github.lianghang1.paygateway.store.ProfitShareStore;
import io.github.lianghang1.paygateway.store.ReconDiscrepancyStore;
import io.github.lianghang1.paygateway.store.RefundStore;
import io.github.lianghang1.paygateway.store.TransferStore;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关运行时上下文：引擎内部各编排组件共享的依赖与协作对象（package-private 传递）。
 *
 * <p>由 PaymentGateway.Builder 组装，构建后字段只读；now() 统一走 Clock 便于测试注入。</p>
 *
 * @author lianghang
 */
final class GatewayRuntime {

    final ChannelRegistry registry;
    final ChannelConfigProvider configProvider;
    final PaymentStore paymentStore;
    final RefundStore refundStore;
    final TransferStore transferStore;
    final ProfitShareStore profitShareStore;
    final NotifyLogStore notifyLogStore;
    final ReconDiscrepancyStore reconDiscrepancyStore;
    final DownstreamNotifier notifier;
    final Clock clock;
    final EventPublisher publisher;
    final StateTransitions transitions;

    GatewayRuntime(ChannelRegistry registry,
                   ChannelConfigProvider configProvider,
                   PaymentStore paymentStore,
                   RefundStore refundStore,
                   TransferStore transferStore,
                   ProfitShareStore profitShareStore,
                   NotifyLogStore notifyLogStore,
                   ReconDiscrepancyStore reconDiscrepancyStore,
                   DownstreamNotifier notifier,
                   Clock clock,
                   List<PaymentEventListener> listeners) {
        this.registry = registry;
        this.configProvider = configProvider;
        this.paymentStore = paymentStore;
        this.refundStore = refundStore;
        this.transferStore = transferStore;
        this.profitShareStore = profitShareStore;
        this.notifyLogStore = notifyLogStore;
        this.reconDiscrepancyStore = reconDiscrepancyStore;
        this.notifier = notifier;
        this.clock = clock;
        this.publisher = new EventPublisher(listeners);
        this.transitions = new StateTransitions(this);
    }

    /** 统一取当前时间（测试可注入固定 Clock）。 */
    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
