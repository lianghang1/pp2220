package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * starter 装配测试：验证自动配置、配置绑定，以及经 Spring Bean 跑通无台账开票流程。
 */
@SpringBootTest(classes = InvoiceAutoConfigurationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "conflux.invoice.configs.demo.mock.seller-tax-no=91310000TEST000001",
        "conflux.invoice.configs.demo.mock.notify-url=http://127.0.0.1/callback/mock",
        "conflux.invoice.configs.demo.mock.sandbox=true",
        "conflux.invoice.configs.demo.mock.attr.appKey=demo-app-key",
        "conflux.invoice.configs.demo.mock.attr.appSecret=demo-secret",
        "conflux.invoice.configs.demo.mock.attr.issueOutcome=AUTH",
        "conflux.invoice.configs.demo.mock.attr.redConfirmOutcome=CONFIRMED"
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
        assertTrue(gateway.registeredChannels().contains("mock"));
    }

    @Test
    void propertiesConfigProviderShouldResolveConfig() {
        assertEquals("demo", configProvider
                .getConfig("demo", "mock", "91310000TEST000001").getConfigId());
        assertEquals("AUTH", ((io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceParams)
                configProvider.getConfig("demo", "mock", "91310000TEST000001"))
                .getIssueOutcome());
    }

    @Test
    void fullFlowThroughSpringBootContext() {
        InvoiceRequest request = new InvoiceRequest()
                .setConfigId("demo").setBizOrderNo("SB-1")
                .setSellerTaxNo("91310000TEST000001").setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("服务费").setAmount(new BigDecimal("100.00"))))
                .setAmount(new BigDecimal("100.00"));
        InvoiceResult first = gateway.issue(request);
        assertSame(InvoiceState.WAITING_AUTH, first.getState());
        assertNotNull(first.getAuthChallenge());
        assertNotNull(first.getInvoice());

        InvoiceResult done = gateway.submitAuth(new AuthSubmission()
                .setConfigId("demo")
                .setSession(first.getAuthSession())
                .setInvoice(first.getInvoice())
                .setCaptcha("123456"));
        assertSame(InvoiceState.SUCCEEDED, done.getState());
        assertTrue(done.hasLegalXml());
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
