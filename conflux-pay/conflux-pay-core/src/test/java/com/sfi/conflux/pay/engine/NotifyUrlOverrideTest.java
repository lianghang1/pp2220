package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.api.PaymentRequest;
import com.sfi.conflux.pay.api.RefundRequest;
import com.sfi.conflux.pay.api.TransferRequest;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelProvider;
import com.sfi.conflux.pay.channel.PayMethod;
import com.sfi.conflux.pay.channel.PaymentHandler;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PaymentSnapshot;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.channel.TransferHandler;
import com.sfi.conflux.pay.channel.TransferInvocation;
import com.sfi.conflux.pay.channel.TransferSnapshot;
import com.sfi.conflux.pay.config.ChannelConfigProvider;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证按单 notifyUrl：请求值作为一等字段经模型透传给渠道 handler，
 * handler 以「请求优先、配置兜底」取值，且不篡改配置源。
 *
 * <p>用自定义渠道捕获 handler 最终采用的 notifyUrl（复现真实渠道
 * {@code defaultIfBlank(模型值, 配置值)} 的取法），走真实编排链路，不触网。</p>
 *
 * @author lianghang
 */
class NotifyUrlOverrideTest {

    private static final String APP = "app-notify";
    private static final String CHANNEL = "notify-channel";
    private static final String METHOD = "NOTIFY_PAY";
    private static final String CONFIG_URL = "https://config.example.com/callback";
    private static final String DYNAMIC = "https://order.example.com/callback";

    @BeforeAll
    static void registerMethod() {
        try {
            PayMethod.register(METHOD, CHANNEL);
        } catch (IllegalStateException ignored) {
            // 同 JVM 重复注册忽略
        }
    }

    // ============ 支付下单 ============

    @Test
    void payRequestShouldWinOverConfig() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createPayment(new PaymentRequest()
                .setConfigId(APP).setBizOrderNo("NO-1").setMethod(METHOD)
                .setAmount(100L).setNotifyUrl(DYNAMIC));
        assertThat(provider.payNotifyUrl.get()).isEqualTo(DYNAMIC);
    }

    @Test
    void payShouldFallBackToConfigWhenAbsent() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createPayment(new PaymentRequest()
                .setConfigId(APP).setBizOrderNo("NO-2").setMethod(METHOD).setAmount(100L));
        assertThat(provider.payNotifyUrl.get()).isEqualTo(CONFIG_URL);
    }

    // ============ 退款 ============

    @Test
    void refundRequestShouldWinOverConfig() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createRefund(new RefundRequest()
                .setConfigId(APP).setBizRefundNo("R-1").setChannelCode(CHANNEL)
                .setBizOrderNo("NO-1").setTotalAmount(100L).setAmount(100L)
                .setNotifyUrl(DYNAMIC));
        assertThat(provider.refundNotifyUrl.get()).isEqualTo(DYNAMIC);
    }

    @Test
    void refundShouldFallBackToConfigWhenAbsent() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createRefund(new RefundRequest()
                .setConfigId(APP).setBizRefundNo("R-2").setChannelCode(CHANNEL)
                .setBizOrderNo("NO-1").setTotalAmount(100L).setAmount(100L));
        assertThat(provider.refundNotifyUrl.get()).isEqualTo(CONFIG_URL);
    }

    // ============ 转账 ============

    @Test
    void transferRequestShouldWinOverConfig() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createTransfer(new TransferRequest()
                .setConfigId(APP).setBizTransferNo("T-1").setMethod(METHOD)
                .setPayeeAccount("payee").setAmount(100L).setNotifyUrl(DYNAMIC));
        assertThat(provider.transferNotifyUrl.get()).isEqualTo(DYNAMIC);
    }

    @Test
    void transferShouldFallBackToConfigWhenAbsent() {
        CapturingProvider provider = new CapturingProvider();
        gateway(provider).createTransfer(new TransferRequest()
                .setConfigId(APP).setBizTransferNo("T-2").setMethod(METHOD)
                .setPayeeAccount("payee").setAmount(100L));
        assertThat(provider.transferNotifyUrl.get()).isEqualTo(CONFIG_URL);
    }

    /** 请求值不污染配置源：随后不带 notifyUrl 的下单仍拿到配置值。 */
    @Test
    void requestValueShouldNotMutateConfigSource() {
        CapturingProvider provider = new CapturingProvider();
        PaymentGateway gateway = gateway(provider);
        gateway.createPayment(new PaymentRequest()
                .setConfigId(APP).setBizOrderNo("NO-3").setMethod(METHOD)
                .setAmount(100L).setNotifyUrl(DYNAMIC));
        gateway.createPayment(new PaymentRequest()
                .setConfigId(APP).setBizOrderNo("NO-4").setMethod(METHOD).setAmount(100L));
        assertThat(provider.payNotifyUrl.get()).isEqualTo(CONFIG_URL);
    }

    private PaymentGateway gateway(CapturingProvider provider) {
        return PaymentGateway.builder()
                .channel(provider)
                .configProvider(new ChannelConfigProvider() {
                    @Override
                    public ChannelConfig getConfig(String configId, String channelCode) {
                        return new ChannelConfig()
                                .setConfigId(configId)
                                .setChannelCode(channelCode)
                                .setNotifyUrl(CONFIG_URL);
                    }
                })
                .build();
    }

    /** 捕获 handler 最终采用的 notifyUrl；模拟真实渠道的「请求优先、配置兜底」取法。 */
    private static final class CapturingProvider implements ChannelProvider {

        final AtomicReference<String> payNotifyUrl = new AtomicReference<String>();
        final AtomicReference<String> refundNotifyUrl = new AtomicReference<String>();
        final AtomicReference<String> transferNotifyUrl = new AtomicReference<String>();

        @Override
        public String channelCode() {
            return CHANNEL;
        }

        @Override
        public Set<String> supportedMethods() {
            return Collections.singleton(METHOD);
        }

        @Override
        public PaymentHandler paymentHandler() {
            return new PaymentHandler() {
                @Override
                public PaymentInvocation createPayment(com.sfi.conflux.pay.model.Payment payment,
                                                       ChannelConfig config) {
                    payNotifyUrl.set(StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl()));
                    return new PaymentInvocation().setChannelOrderNo("CH-" + payment.getBizOrderNo())
                            .setState(PaymentState.PAYING);
                }

                @Override
                public PaymentSnapshot queryPayment(com.sfi.conflux.pay.model.Payment payment,
                                                    ChannelConfig config) {
                    return new PaymentSnapshot().setState(PaymentState.PAYING);
                }

                @Override
                public void closePayment(com.sfi.conflux.pay.model.Payment payment,
                                         ChannelConfig config) {
                    // no-op
                }
            };
        }

        @Override
        public RefundHandler refundHandler() {
            return new RefundHandler() {
                @Override
                public RefundInvocation createRefund(com.sfi.conflux.pay.model.Refund refund,
                                                     com.sfi.conflux.pay.model.Payment payment,
                                                     ChannelConfig config) {
                    refundNotifyUrl.set(StringUtils.defaultIfBlank(refund.getNotifyUrl(), config.getNotifyUrl()));
                    return new RefundInvocation().setChannelRefundNo("CR-" + refund.getBizRefundNo());
                }

                @Override
                public RefundSnapshot queryRefund(com.sfi.conflux.pay.model.Refund refund,
                                                  ChannelConfig config) {
                    return new RefundSnapshot();
                }
            };
        }

        @Override
        public TransferHandler transferHandler() {
            return new TransferHandler() {
                @Override
                public TransferInvocation createTransfer(com.sfi.conflux.pay.model.Transfer transfer,
                                                         ChannelConfig config) {
                    transferNotifyUrl.set(StringUtils.defaultIfBlank(transfer.getNotifyUrl(), config.getNotifyUrl()));
                    return new TransferInvocation().setChannelTransferNo("CT-" + transfer.getBizTransferNo());
                }

                @Override
                public TransferSnapshot queryTransfer(com.sfi.conflux.pay.model.Transfer transfer,
                                                      ChannelConfig config) {
                    return new TransferSnapshot();
                }
            };
        }
    }
}
