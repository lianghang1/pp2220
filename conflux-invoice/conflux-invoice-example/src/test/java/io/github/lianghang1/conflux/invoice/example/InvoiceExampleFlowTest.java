package io.github.lianghang1.conflux.invoice.example;

import io.github.lianghang1.conflux.invoice.api.AuthSubmission;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.api.RedApplyRequest;
import io.github.lianghang1.conflux.invoice.api.RedIssueRequest;
import io.github.lianghang1.conflux.invoice.engine.InvoiceGateway;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** example 宿主全流程：验证 yml 配置、starter 装配与内存 Store 组合可运行。 */
@SpringBootTest(classes = InvoiceExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvoiceExampleFlowTest {

    private static final String TAX = "91310000MOCK000001";

    @Autowired
    private InvoiceGateway gateway;

    @Test
    void endToEndBlueAndRedInvoiceFlow() {
        InvoiceRequest request = new InvoiceRequest()
                .setAppId("demo").setBizOrderNo("EX-1")
                .setSellerTaxNo(TAX).setBuyerTitle("示例购方企业")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("咨询服务").setAmount(new BigDecimal("1000.00"))))
                .setAmount(new BigDecimal("1000.00"))
                .setTaxAmount(new BigDecimal("60.00"));

        InvoiceResult first = gateway.issue(request);
        assertSame(InvoiceState.WAITING_AUTH, first.getState());
        assertNotNull(first.getAuthChallenge());

        InvoiceResult issued = gateway.submitAuth(new AuthSubmission()
                .setSessionId(first.getAuthChallenge().getSessionId()).setCaptcha("6666"));
        assertSame(InvoiceState.SUCCEEDED, issued.getState());
        assertTrue(issued.hasLegalXml());
        assertEquals(3, gateway.files(issued.getInvoiceId()).size());

        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setAppId("demo").setBizRedNo("EX-R1")
                .setOriginalInvoiceId(issued.getInvoiceId()).setReason("抬头填错"));
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
        assertSame(RedApplyState.CONFIRMED,
                gateway.confirmRed(apply.getRedApplyId()).getState());

        InvoiceResult red = gateway.issueRed(new RedIssueRequest()
                .setAppId("demo").setRedApplyId(apply.getRedApplyId()));
        assertSame(InvoiceState.SUCCEEDED, red.getState());
        assertSame(InvoiceState.RED_SUCCEEDED,
                gateway.findById(issued.getInvoiceId()).getState());
    }
}
