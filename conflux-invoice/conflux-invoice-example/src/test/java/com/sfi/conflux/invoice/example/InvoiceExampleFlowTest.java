package com.sfi.conflux.invoice.example;

import com.sfi.conflux.invoice.api.AuthSubmission;
import com.sfi.conflux.invoice.api.InvoiceRequest;
import com.sfi.conflux.invoice.api.InvoiceResult;
import com.sfi.conflux.invoice.api.RedApplyQueryRequest;
import com.sfi.conflux.invoice.api.RedApplyRequest;
import com.sfi.conflux.invoice.api.RedIssueRequest;
import com.sfi.conflux.invoice.engine.InvoiceGateway;
import com.sfi.conflux.invoice.model.InvoiceItem;
import com.sfi.conflux.invoice.model.InvoiceState;
import com.sfi.conflux.invoice.model.InvoiceType;
import com.sfi.conflux.invoice.model.RedApply;
import com.sfi.conflux.invoice.model.RedApplyState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * example 宿主全流程：验证 yml 配置、starter 装配与无台账载体往返可运行。
 */
@SpringBootTest(classes = InvoiceExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvoiceExampleFlowTest {
    private static final String TAX = "91310000MOCK000001";

    @Autowired
    private InvoiceGateway gateway;

    @Test
    void endToEndBlueAndRedInvoiceFlow() {
        InvoiceRequest request = new InvoiceRequest()
                .setConfigId("demo").setBizOrderNo("EX-1")
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
                .setConfigId("demo")
                .setSession(first.getAuthSession())
                .setInvoice(first.getInvoice())
                .setCaptcha("6666"));
        assertSame(InvoiceState.SUCCEEDED, issued.getState());
        assertTrue(issued.hasLegalXml());
        assertEquals(3, issued.getFiles().size());

        // 两段红冲：载体往返
        RedApply apply = gateway.applyRed(new RedApplyRequest()
                .setConfigId("demo").setBizRedNo("EX-R1")
                .setOriginalInvoice(issued.getInvoice()).setReason("抬头填错"));
        assertSame(RedApplyState.WAITING_CONFIRM, apply.getState());
        RedApply confirmed = gateway.queryRedApply(new RedApplyQueryRequest()
                .setConfigId("demo").setRedApply(apply));
        assertSame(RedApplyState.CONFIRMED, confirmed.getState());

        InvoiceResult red = gateway.issueRed(new RedIssueRequest()
                .setConfigId("demo").setRedApply(confirmed).setOriginalInvoice(issued.getInvoice()));
        assertSame(InvoiceState.SUCCEEDED, red.getState());
        assertTrue(red.hasLegalXml());
    }
}
