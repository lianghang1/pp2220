package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 内存 Store 参考实现的基础能力测试。 */
class InMemoryStoreContractTest {

    private static final String TAX = "91310000TEST000001";

    @Test
    void findByInvoiceNoLocatesOriginalForRedApply() {
        InMemoryInvoiceStore store = new InMemoryInvoiceStore();
        Invoice invoice = invoice("I1", "B1", InvoiceState.SUCCEEDED)
                .setInvoiceNo("FP0001").setInvoiceCode("DM0001");
        store.save(invoice);
        assertEquals("I1", store.findByInvoiceNo("FP0001", "DM0001").getInvoiceId());
    }

    @Test
    void fileStoreIsIdempotentPerType() {
        InMemoryInvoiceFileStore store = new InMemoryInvoiceFileStore();
        store.saveAll(Collections.singletonList(
                new InvoiceFile().setInvoiceId("I1").setType(InvoiceFileType.XML)
                        .setContent("<a/>")));
        store.saveAll(Collections.singletonList(
                new InvoiceFile().setInvoiceId("I1").setType(InvoiceFileType.XML)
                        .setContent("<b/>")));
        assertEquals(1, store.findByInvoiceId("I1").size());
        assertEquals("<b/>", store.findByInvoiceIdAndType("I1", InvoiceFileType.XML)
                .get(0).getContent());
    }

    @Test
    void configProviderRejectsDuplicateAccountKey() {
        InMemoryInvoiceConfigProvider provider = new InMemoryInvoiceConfigProvider();
        provider.register(config("demo", "app-1"), instance("demo", "app-1"));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> provider.register(config("other", "app-1"), instance("other", "app-1")));
        assertSame(InvoiceErrorCode.CONFIG_CONFLICT, ex.getErrorCode());
    }

    @Test
    void configProviderIndexesAllThreeDimensions() {
        InMemoryInvoiceConfigProvider provider = new InMemoryInvoiceConfigProvider();
        provider.register(config("demo", "app-1"), instance("demo", "app-1"));
        assertEquals("app-1#" + TAX,
                provider.getConfig("demo", "mock", TAX).getAccountKey());
        assertEquals("demo", provider.getByAccountKey("mock", "app-1#" + TAX).getAppId());
        assertEquals(1, provider.instances("demo", TAX).size());
    }

    private Invoice invoice(String id, String biz, InvoiceState state) {
        return new Invoice().setInvoiceId(id).setAppId("demo").setBizOrderNo(biz)
                .setSellerTaxNo(TAX).setState(state)
                .setCreatedTime(LocalDateTime.now()).setUpdatedTime(LocalDateTime.now());
    }

    private ChannelConfig config(String appId, String appKey) {
        return new ChannelConfig().setAppId(appId).setChannelCode("mock")
                .setSellerTaxNo(TAX)
                .setAccountKey(InvoiceAccountKey.of(appKey, TAX))
                .setParams(new InvoiceChannelParams() {
                    @Override
                    public String channelAppId() {
                        return appKey;
                    }

                    @Override
                    public String sellerTaxNo() {
                        return TAX;
                    }
                });
    }

    private ChannelInstance instance(String appId, String appKey) {
        return new ChannelInstance().setInstanceId(appId + "::mock").setAppId(appId)
                .setChannelCode("mock").setAccountKey(InvoiceAccountKey.of(appKey, TAX))
                .setSellerTaxNo(TAX).setEnabled(true);
    }
}
