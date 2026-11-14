package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.api.ChannelCallbackReply;
import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;
import io.github.lianghang1.paygateway.api.PaymentQueryRequest;
import io.github.lianghang1.paygateway.api.PaymentRequest;
import io.github.lianghang1.paygateway.api.PaymentResult;
import io.github.lianghang1.paygateway.api.ProfitShareQueryRequest;
import io.github.lianghang1.paygateway.api.ProfitShareRequest;
import io.github.lianghang1.paygateway.api.ProfitShareResult;
import io.github.lianghang1.paygateway.api.ReconReport;
import io.github.lianghang1.paygateway.api.RefundQueryRequest;
import io.github.lianghang1.paygateway.api.RefundRequest;
import io.github.lianghang1.paygateway.api.RefundResult;
import io.github.lianghang1.paygateway.api.TransferQueryRequest;
import io.github.lianghang1.paygateway.api.TransferRequest;
import io.github.lianghang1.paygateway.api.TransferResult;
import io.github.lianghang1.paygateway.channel.ChannelProvider;
import io.github.lianghang1.paygateway.channel.ChannelRegistry;
import io.github.lianghang1.paygateway.config.ChannelConfigProvider;
import io.github.lianghang1.paygateway.event.PaymentEventListener;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.store.NotifyLogStore;
import io.github.lianghang1.paygateway.store.PaymentStore;
import io.github.lianghang1.paygateway.store.ProfitShareStore;
import io.github.lianghang1.paygateway.store.ReconDiscrepancyStore;
import io.github.lianghang1.paygateway.store.RefundStore;
import io.github.lianghang1.paygateway.store.TransferStore;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付网关门面：统一支付 API 的唯一入口，双形态共享——
 * 纯 jar 场景经 {@link #builder()} 手动组装，Spring Boot 场景由 starter 装配为单例 Bean。
 *
 * <p>能力入口：下单/查单/关单、退款、转账、分账、渠道回调处理、对账、下游通知重试兜底。
 * 必填依赖为渠道配置提供者与支付单存储；退款/转账/分账/通知/对账存储按需注册，
 * 缺失时对应能力在入口处报明确异常（STORE_MISSING）。</p>
 *
 * <p>线程安全：构建后不可变，所有方法可并发调用。</p>
 *
 * @author lianghang
 */
public class PaymentGateway {

    private final GatewayRuntime runtime;
    private final PaymentOrchestrator paymentOrchestrator;
    private final RefundOrchestrator refundOrchestrator;
    private final TransferOrchestrator transferOrchestrator;
    private final ProfitShareOrchestrator profitShareOrchestrator;
    private final CallbackDispatcher callbackDispatcher;
    private final ReconciliationRunner reconciliationRunner;

    private PaymentGateway(Builder builder) {
        DownstreamNotifier notifier = builder.notifyLogStore == null
                ? NoopNotifier.instance()
                : (builder.notifier != null ? builder.notifier
                : new HttpDownstreamNotifier(builder.notifyLogStore,
                new RetryPolicy(builder.notifyRetrySeconds), builder.notifySecret,
                builder.notifyHttpTimeoutMillis, builder.clock));
        this.runtime = new GatewayRuntime(builder.registry, builder.configProvider, builder.paymentStore,
                builder.refundStore, builder.transferStore, builder.profitShareStore,
                builder.notifyLogStore, builder.reconDiscrepancyStore, notifier,
                builder.clock, builder.listeners);
        this.paymentOrchestrator = new PaymentOrchestrator(runtime);
        this.refundOrchestrator = new RefundOrchestrator(runtime);
        this.transferOrchestrator = new TransferOrchestrator(runtime);
        this.profitShareOrchestrator = new ProfitShareOrchestrator(runtime);
        this.callbackDispatcher = new CallbackDispatcher(runtime);
        this.reconciliationRunner = new ReconciliationRunner(runtime);
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 注册渠道提供者（启动期调用）。 */
    public void registerChannel(ChannelProvider provider) {
        runtime.registry.register(provider);
    }

    /** 已注册渠道编码集合。 */
    public java.util.Set<String> registeredChannels() {
        return runtime.registry.registeredChannels();
    }

    // ---------- 支付 ----------

    /** 统一下单。 */
    public PaymentResult createPayment(PaymentRequest request) {
        return paymentOrchestrator.create(request);
    }

    /** 支付单查询（非终态订单自动向渠道复核，兼做补单）。 */
    public PaymentResult queryPayment(PaymentQueryRequest request) {
        return paymentOrchestrator.query(request);
    }

    /** 关闭支付单。 */
    public PaymentResult closePayment(String paymentId) {
        return paymentOrchestrator.close(paymentId);
    }

    // ---------- 退款 ----------

    /** 发起退款。 */
    public RefundResult createRefund(RefundRequest request) {
        return refundOrchestrator.create(request);
    }

    /** 退款单查询（处理中自动向渠道复核）。 */
    public RefundResult queryRefund(RefundQueryRequest request) {
        return refundOrchestrator.query(request);
    }

    // ---------- 转账 ----------

    /** 发起转账。 */
    public TransferResult createTransfer(TransferRequest request) {
        return transferOrchestrator.create(request);
    }

    /** 转账单查询（处理中自动向渠道复核）。 */
    public TransferResult queryTransfer(TransferQueryRequest request) {
        return transferOrchestrator.query(request);
    }

    // ---------- 分账 ----------

    /** 发起分账。 */
    public ProfitShareResult createProfitShare(ProfitShareRequest request) {
        return profitShareOrchestrator.create(request);
    }

    /** 分账单查询（处理中自动向渠道复核）。 */
    public ProfitShareResult queryProfitShare(ProfitShareQueryRequest request) {
        return profitShareOrchestrator.query(request);
    }

    // ---------- 渠道回调 ----------

    /**
     * 处理渠道回调：使用方在自己的回调入口收集原始请求后调用本方法，
     * 将返回的应答原样写回渠道。验签失败/订单不存在/金额不一致等异常上抛，
     * 由使用方决定以失败应答触发渠道重推。
     */
    public ChannelCallbackReply handleCallback(ChannelCallbackRequest request) {
        return callbackDispatcher.handle(request);
    }

    /** 渠道认可的处理成功应答报文（回调处理异常时使用方以失败应答触发渠道重推）。 */
    public String callbackSuccessReply(String channelCode) {
        return runtime.registry.callbackHandler(channelCode).successReply();
    }

    /** 渠道认可的处理失败应答报文。 */
    public String callbackFailureReply(String channelCode) {
        return runtime.registry.callbackHandler(channelCode).failureReply();
    }

    // ---------- 对账 ----------

    /** 执行指定应用、渠道、日期的对账（建议由使用方定时任务每日调用）。 */
    public ReconReport reconcile(String channelCode, String appId, LocalDate billDate) {
        return reconciliationRunner.reconcile(channelCode, appId, billDate);
    }

    // ---------- 下游通知 ----------

    /** 投递到期未完成的下游通知（建议由使用方定时任务每 10~15 秒调用）。 */
    public void retryPendingNotifies(int limit) {
        runtime.notifier.dispatchDue(limit);
    }

    /** 网关构建器。 */
    public static class Builder {

        private final ChannelRegistry registry = new ChannelRegistry();
        private ChannelConfigProvider configProvider;
        private PaymentStore paymentStore;
        private RefundStore refundStore;
        private TransferStore transferStore;
        private ProfitShareStore profitShareStore;
        private NotifyLogStore notifyLogStore;
        private ReconDiscrepancyStore reconDiscrepancyStore;
        private final List<PaymentEventListener> listeners = new ArrayList<PaymentEventListener>();
        private DownstreamNotifier notifier;
        private int[] notifyRetrySeconds = RetryPolicy.DEFAULT_DELAYS_SECONDS;
        private String notifySecret;
        private long notifyHttpTimeoutMillis = 10_000L;
        private Clock clock = Clock.systemDefaultZone();

        /** 注册渠道提供者（替代外部自行组装 ChannelRegistry）。 */
        public Builder channel(ChannelProvider provider) {
            registry.register(provider);
            return this;
        }

        /** 渠道配置提供者（必填）。 */
        public Builder configProvider(ChannelConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** 支付单存储（必填）。 */
        public Builder paymentStore(PaymentStore paymentStore) {
            this.paymentStore = paymentStore;
            return this;
        }

        /** 退款单存储（退款功能需要）。 */
        public Builder refundStore(RefundStore refundStore) {
            this.refundStore = refundStore;
            return this;
        }

        /** 转账单存储（转账功能需要）。 */
        public Builder transferStore(TransferStore transferStore) {
            this.transferStore = transferStore;
            return this;
        }

        /** 分账单存储（分账功能需要）。 */
        public Builder profitShareStore(ProfitShareStore profitShareStore) {
            this.profitShareStore = profitShareStore;
            return this;
        }

        /** 通知记录存储（下游通知功能需要）。 */
        public Builder notifyLogStore(NotifyLogStore notifyLogStore) {
            this.notifyLogStore = notifyLogStore;
            return this;
        }

        /** 对账差错存储（对账功能需要）。 */
        public Builder reconDiscrepancyStore(ReconDiscrepancyStore reconDiscrepancyStore) {
            this.reconDiscrepancyStore = reconDiscrepancyStore;
            return this;
        }

        /** 追加订单事件监听器（可多次调用）。 */
        public Builder listener(PaymentEventListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
            return this;
        }

        /** 自定义通知投递器（缺省为 HTTP 投递器）。 */
        public Builder notifier(DownstreamNotifier notifier) {
            this.notifier = notifier;
            return this;
        }

        /** 下游通知重试间隔（秒），缺省 15/15/30/3m/10m/20m/30m…。 */
        public Builder notifyRetrySeconds(int[] notifyRetrySeconds) {
            this.notifyRetrySeconds = notifyRetrySeconds;
            return this;
        }

        /** 下游通知 HMAC 签名密钥（null 表示不签名）。 */
        public Builder notifySecret(String notifySecret) {
            this.notifySecret = notifySecret;
            return this;
        }

        /** 下游通知单次投递超时（毫秒）。 */
        public Builder notifyHttpTimeoutMillis(long notifyHttpTimeoutMillis) {
            this.notifyHttpTimeoutMillis = notifyHttpTimeoutMillis;
            return this;
        }

        /** 时钟（测试注入固定时钟用）。 */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** 构建；渠道配置提供者与支付单存储必填。 */
        public PaymentGateway build() {
            if (configProvider == null) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING, "未设置渠道配置提供者");
            }
            if (paymentStore == null) {
                throw new GatewayException(GatewayErrorCode.STORE_MISSING, "未设置支付单存储");
            }
            return new PaymentGateway(this);
        }
    }
}
