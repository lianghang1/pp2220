package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.api.BillSheet;
import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.api.ProfitShareQueryRequest;
import io.github.lianghang1.conflux.pay.api.ProfitShareRequest;
import io.github.lianghang1.conflux.pay.api.ProfitShareResult;
import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.api.TransferQueryRequest;
import io.github.lianghang1.conflux.pay.api.TransferRequest;
import io.github.lianghang1.conflux.pay.api.TransferResult;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.ChannelRegistry;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 聚合支付网关门面：统一支付 API 的唯一入口，双形态共享——
 * 纯 jar 场景经 {@link #builder()} 手动组装，Spring Boot 场景由 starter 装配为单例 Bean。
 *
 * <p>SDK 为无状态渠道聚合层：统一出入参、统一结果归一、回调验签归一、账单拉取归一。
 * 不持有台账、不推进状态、不做业务校验——落库、状态推进、幂等判重、金额校验、
 * 业务联动（订单/库存等）全部由使用方完成。</p>
 *
 * <p>接入契约：渠道受理成功不等于业务最终态，最终态来自渠道回调或查单，
 * 使用方必须实现回调入口或定时查单（至少其一）。</p>
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

    private PaymentGateway(Builder builder) {
        this.runtime = new GatewayRuntime(builder.registry, builder.configProvider, builder.clock);
        this.paymentOrchestrator = new PaymentOrchestrator(runtime);
        this.refundOrchestrator = new RefundOrchestrator(runtime);
        this.transferOrchestrator = new TransferOrchestrator(runtime);
        this.profitShareOrchestrator = new ProfitShareOrchestrator(runtime);
        this.callbackDispatcher = new CallbackDispatcher(runtime);
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

    /** 统一下单：调用渠道，返回归一受理结果（唤起参数或即时扣款结果）。 */
    public PaymentResult createPayment(PaymentRequest request) {
        return paymentOrchestrator.create(request);
    }

    /** 渠道查单：返回渠道侧归一快照，状态推进由使用方完成。 */
    public PaymentResult queryPayment(PaymentQueryRequest request) {
        return paymentOrchestrator.query(request);
    }

    /** 关闭渠道订单；渠道报已支付时自动复查一次并按渠道真实状态返回。 */
    public PaymentResult closePayment(PaymentQueryRequest request) {
        return paymentOrchestrator.close(request);
    }

    // ---------- 退款 ----------

    /** 发起退款（需携带原单金额与原单渠道单号）。 */
    public RefundResult createRefund(RefundRequest request) {
        return refundOrchestrator.create(request);
    }

    /** 渠道退款查单。 */
    public RefundResult queryRefund(RefundQueryRequest request) {
        return refundOrchestrator.query(request);
    }

    // ---------- 转账 ----------

    /** 发起转账。 */
    public TransferResult createTransfer(TransferRequest request) {
        return transferOrchestrator.create(request);
    }

    /** 渠道转账查单。 */
    public TransferResult queryTransfer(TransferQueryRequest request) {
        return transferOrchestrator.query(request);
    }

    // ---------- 分账 ----------

    /** 发起分账（需携带原支付单渠道单号）。 */
    public ProfitShareResult createProfitShare(ProfitShareRequest request) {
        return profitShareOrchestrator.create(request);
    }

    /** 渠道分账查单。 */
    public ProfitShareResult queryProfitShare(ProfitShareQueryRequest request) {
        return profitShareOrchestrator.query(request);
    }

    // ---------- 渠道回调 ----------

    /**
     * 验签并解析渠道回调，返回归一化事件。使用方查自己 DB 得到 configId 后显式传入，
     * 框架据此取配置验签——不再从回调报文隐式反查账户键。
     * 使用方按事件中的业务单号定位自己的订单，判重、金额校验与状态推进自行完成后，
     * 将应答报文写回渠道。
     */
    public CallbackEvent parseCallback(String configId, ChannelCallbackRequest request) {
        return callbackDispatcher.parse(configId, request);
    }

    /** 渠道认可的处理成功应答报文（回调处理异常时使用方以失败应答触发渠道重推）。 */
    public String callbackSuccessReply(String channelCode) {
        return runtime.registry.callbackHandler(channelCode).successReply();
    }

    /** 渠道认可的处理失败应答报文。 */
    public String callbackFailureReply(String channelCode) {
        return runtime.registry.callbackHandler(channelCode).failureReply();
    }

    // ---------- 账单 ----------

    /** 拉取指定应用、指定日期的渠道账单（归一化条目），对账比对由使用方完成。 */
    public BillSheet fetchBill(String channelCode, String configId, LocalDate billDate) {
        return runtime.registry.billHandler(channelCode)
                .fetchBill(configId, billDate, runtime.configProvider.getConfig(configId, channelCode));
    }

    // ---------- 构建 ----------

    /** 网关构建器。 */
    public static class Builder {

        private final ChannelRegistry registry = new ChannelRegistry();
        private ChannelConfigProvider configProvider;
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

        /** 时钟（测试注入固定时钟用）。 */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * 构建；渠道配置提供者必填。
         *
         * <p>内置渠道实现由 {@link io.github.lianghang1.conflux.pay.channel.BuiltinChannels#all()}
         * 经 SPI 全量自动注册（引入 channels 依赖即生效），用户零 {@code .channel()}；
         * 通过 {@link #channel(ChannelProvider)} 注册的自定义渠道在 build 前已进入注册表，
         * 同编码时按"先注册者优先"保留自定义实现（覆盖内置）。</p>
         */
        public PaymentGateway build() {
            if (configProvider == null) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING, "未设置渠道配置提供者");
            }
            for (io.github.lianghang1.conflux.pay.channel.ChannelProvider builtin
                    : io.github.lianghang1.conflux.pay.channel.BuiltinChannels.all()) {
                if (builtin != null) {
                    registry.register(builtin);
                }
            }
            return new PaymentGateway(this);
        }
    }
}
