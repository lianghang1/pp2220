package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.pay.engine.DownstreamNotifier;
import io.github.lianghang1.conflux.pay.engine.PaymentGateway;
import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.NotifyLogStoreImpl;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.PaymentStoreImpl;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.ProfitShareStoreImpl;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.ReconDiscrepancyStoreImpl;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.RefundStoreImpl;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.TransferStoreImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试装配台：组装含全部内存 Store 与模拟渠道的网关实例，并捕获下游通知。
 *
 * @author lianghang
 */
public final class GatewayTestHarness {

    public static final String APP_ID = "app-001";

    public final PaymentStoreImpl paymentStore = new PaymentStoreImpl();
    public final RefundStoreImpl refundStore = new RefundStoreImpl();
    public final TransferStoreImpl transferStore = new TransferStoreImpl();
    public final ProfitShareStoreImpl profitShareStore = new ProfitShareStoreImpl();
    public final NotifyLogStoreImpl notifyLogStore = new NotifyLogStoreImpl();
    public final ReconDiscrepancyStoreImpl reconDiscrepancyStore = new ReconDiscrepancyStoreImpl();
    public final TestChannelProvider channelProvider = new TestChannelProvider();
    public final CapturingNotifier notifier = new CapturingNotifier();
    public final PaymentGateway gateway;

    static {
        try {
            io.github.lianghang1.conflux.pay.channel.PayMethod.register(TestChannelProvider.METHOD_PAY, TestChannelProvider.CODE);
            io.github.lianghang1.conflux.pay.channel.PayMethod.register(TestChannelProvider.METHOD_TRANS, TestChannelProvider.CODE);
        } catch (IllegalStateException ignored) {
            // 同 JVM 内重复构建时忽略
        }
    }

    public GatewayTestHarness() {
        this(new io.github.lianghang1.conflux.pay.event.PaymentEventListener[0]);
    }

    public GatewayTestHarness(io.github.lianghang1.conflux.pay.event.PaymentEventListener... listeners) {
        io.github.lianghang1.conflux.pay.engine.PaymentGateway.Builder builder = io.github.lianghang1.conflux.pay.engine.PaymentGateway.builder()
                .channel(channelProvider)
                .configProvider(new TestChannelProvider.TestConfigProvider(APP_ID))
                .paymentStore(paymentStore)
                .refundStore(refundStore)
                .transferStore(transferStore)
                .profitShareStore(profitShareStore)
                .notifyLogStore(notifyLogStore)
                .reconDiscrepancyStore(reconDiscrepancyStore)
                .notifier(notifier);
        for (io.github.lianghang1.conflux.pay.event.PaymentEventListener listener : listeners) {
            builder.listener(listener);
        }
        this.gateway = builder.build();
    }

    /** 通知捕获器：记录 submit 的通知记录。 */
    public static class CapturingNotifier implements DownstreamNotifier {

        public final List<NotifyLog> submitted = new CopyOnWriteArrayList<NotifyLog>();

        @Override
        public void submit(NotifyLog notifyLog) {
            submitted.add(notifyLog);
        }

        @Override
        public void dispatchDue(int limit) {
            // 测试捕获器不做真实投递
        }
    }
}
