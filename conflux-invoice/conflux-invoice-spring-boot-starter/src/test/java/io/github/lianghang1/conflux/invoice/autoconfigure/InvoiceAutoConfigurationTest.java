package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceFileStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryAuthSessionStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceFileStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryRedApplyStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * starter 装配测试：验证自动配置、配置绑定（含 attrs 中的 camelCase 键）、
 * 以及通过 Spring Bean 跑通授权开票全流程。
 */
@SpringBootTest(classes = InvoiceAutoConfigurationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "conflux.invoice.apps.demo.channels.mock.seller-tax-no=91310000TEST000001",
        "conflux.invoice.apps.demo.channels.mock.notify-url=http://127.0.0.1/callback/mock",
        "conflux.invoice.apps.demo.channels.mock.sandbox=true",
        "conflux.invoice.apps.demo.channels.mock.attrs.appKey=demo-app-key",
        "conflux.invoice.apps.demo.channels.mock.attrs.appSecret=demo-secret",
        "conflux.invoice.apps.demo.channels.mock.attrs.issueOutcome=AUTH",
        "conflux.invoice.apps.demo.channels.mock.attrs.redConfirmOutcome=CONFIRMED"
})
class InvoiceAutoConfigurationTest {

    @Autowired
    private InvoiceGateway gateway;

    @Autowired
    private InvoiceConfigProvider configProvider;

    @Test
    void gatewayShouldBeAssembledWithMockChannel() {
        assertTrue(gateway.callbackReply("mock", true, "ok").isSuccess());
        assertNotNull(configProvider.getConfig("demo", "mock", "91310000TEST000001"));
    }

    @Test
    void propertiesConfigProviderShouldResolveBothDirections() {
        assertEquals("demo", configProvider
                .getByAccountKey("mock", "demo-app-key#91310000TEST000001").getAppId());
        assertEquals("AUTH", ((io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceParams)
                configProvider.getConfig("demo", "mock", "91310000TEST000001")
                        .getParams()).getIssueOutcome());
    }

    @Test
    void fullFlowThroughSpringBootContext() {
        InvoiceRequest request = new InvoiceRequest()
                .setAppId("demo").setBizOrderNo("SB-1")
                .setSellerTaxNo("91310000TEST000001").setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("服务费").setAmount(new BigDecimal("100.00"))))
                .setAmount(new BigDecimal("100.00"));
        InvoiceResult first = gateway.issue(request);
        assertSame(InvoiceState.WAITING_AUTH, first.getState());
        assertNotNull(first.getAuthChallenge());

        InvoiceResult done = gateway.submitAuth(new AuthSubmission()
                .setSessionId(first.getAuthChallenge().getSessionId()).setCaptcha("123456"));
        assertSame(InvoiceState.SUCCEEDED, done.getState());
        assertTrue(done.hasLegalXml());
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        InvoiceStore invoiceStore() {
            return new InMemoryInvoiceStore();
        }

        @Bean
        RedApplyStore redApplyStore() {
            return new InMemoryRedApplyStore();
        }

        @Bean
        AuthSessionStore authSessionStore() {
            return new InMemoryAuthSessionStore();
        }

        @Bean
        InvoiceFileStore invoiceFileStore() {
            return new InMemoryInvoiceFileStore();
        }
    }
}
