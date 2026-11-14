package io.github.lianghang1.paygateway.autoconfigure;

import io.github.lianghang1.paygateway.config.ChannelConfigProvider;
import io.github.lianghang1.paygateway.engine.PaymentGateway;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.store.PaymentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter 装配测试：验证自动配置、默认配置提供者与网关 Bean 的完整构建。
 *
 * @author lianghang
 */
@SpringBootTest(classes = PayGatewayAutoConfigurationTest.TestApp.class, properties = {
        "pay-gateway.apps.demo.channels.mock.notifyUrl=http://127.0.0.1/callback/mock",
        "pay-gateway.apps.demo.channels.mock.sandbox=true",
        "pay-gateway.apps.demo.channels.mock.attrs.appKey=demo-app-key",
        "pay-gateway.apps.demo.channels.mock.attrs.payOutcome=paying"
})
class PayGatewayAutoConfigurationTest {

    @Autowired
    private PaymentGateway gateway;

    @Autowired
    private ChannelConfigProvider configProvider;

    @Test
    void gatewayShouldBeAssembledWithMockChannel() {
        assertThat(gateway.registeredChannels()).contains("mock");
    }

    @Test
    void propertiesConfigProviderShouldResolveBothDirections() {
        io.github.lianghang1.paygateway.channel.ChannelConfig config = configProvider.getConfig("demo", "mock");
        assertThat(config.getAppId()).isEqualTo("demo");
        assertThat(config.getNotifyUrl()).isEqualTo("http://127.0.0.1/callback/mock");
        assertThat(config.isSandbox()).isTrue();
        assertThat(configProvider.getByChannelApp("mock", "demo-app-key").getAppId()).isEqualTo("demo");
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        PaymentStore paymentStore() {
            return new InMemoryPaymentStore();
        }
    }

    /** 测试用内存支付单存储。 */
    static class InMemoryPaymentStore implements PaymentStore {

        private final Map<String, Payment> data = new ConcurrentHashMap<String, Payment>();

        @Override
        public void save(Payment payment) {
            data.put(payment.getPaymentId(), payment);
        }

        @Override
        public void update(Payment payment) {
            data.put(payment.getPaymentId(), payment);
        }

        @Override
        public Payment findById(String paymentId) {
            return data.get(paymentId);
        }

        @Override
        public Payment findByBizOrderNo(String appId, String bizOrderNo) {
            for (Payment payment : data.values()) {
                if (payment.getAppId().equals(appId) && payment.getBizOrderNo().equals(bizOrderNo)) {
                    return payment;
                }
            }
            return null;
        }

        @Override
        public Payment findByChannelOrderNo(String channelOrderNo) {
            for (Payment payment : data.values()) {
                if (payment.getChannelOrderNo() != null && payment.getChannelOrderNo().equals(channelOrderNo)) {
                    return payment;
                }
            }
            return null;
        }

        @Override
        public List<Payment> findPayingBefore(LocalDateTime threshold, int limit) {
            return new ArrayList<Payment>();
        }

        @Override
        public List<Payment> findByChannelAndDate(String channelCode, String appId, LocalDate billDate) {
            return new ArrayList<Payment>();
        }
    }
}
