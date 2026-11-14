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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存 Store 参考实现的契约测试：CAS 与租约必须在实现层真实生效，
 * 不能只是接口文档上的口号（这正是支付域 Store 契约缺位之处）。
 */
class InMemoryStoreContractTest {

    private static final String TAX = "91310000TEST000001";

    @Test
    void casRejectsStaleWriteAndKeepsWinner() {
        InMemoryInvoiceStore store = new InMemoryInvoiceStore();
        store.save(invoice("I1", "B1", InvoiceState.CREATED, 0L));

        Invoice a = store.findById("I1");
        Invoice b = store.findById("I1");
        a.setState(InvoiceState.PROCESSING).setVersion(1L);
        assertTrue(store.tryUpdate(a, 0L));

        b.setState(InvoiceState.FAILED).setVersion(1L);
        assertFalse(store.tryUpdate(b, 0L), "旧版本写入必须被拒绝");
        assertSame(InvoiceState.PROCESSING, store.findById("I1").getState());
        assertEquals(1L, store.findById("I1").getVersion());
    }

    @Test
    void duplicateBizOrderNoIsRejected() {
        InMemoryInvoiceStore store = new InMemoryInvoiceStore();
        store.save(invoice("I1", "B1", InvoiceState.CREATED, 0L));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> store.save(invoice("I2", "B1", InvoiceState.CREATED, 0L)));
        assertSame(InvoiceErrorCode.DUPLICATE_BIZ_ORDER, ex.getErrorCode());
    }

    @Test
    void claimLeasePreventsConcurrentOwners() {
        InMemoryInvoiceStore store = new InMemoryInvoiceStore();
        Invoice invoice = invoice("I1", "B1", InvoiceState.PROCESSING, 0L);
        invoice.setUpdatedTime(LocalDateTime.now().minusHours(1L));
        store.save(invoice);

        List<Invoice> first = store.claimProcessingBefore(
                LocalDateTime.now(), 10, "owner-1", 60000L);
        assertEquals(1, first.size());
        assertEquals("owner-1", first.get(0).getClaimOwner());

        List<Invoice> second = store.claimProcessingBefore(
                LocalDateTime.now(), 10, "owner-2", 60000L);
        assertTrue(second.isEmpty(), "他人持有的未过期租约不得被重复认领");

        assertTrue(store.releaseClaim("I1", "owner-1"));
        assertFalse(store.releaseClaim("I1", "owner-2"));
        List<Invoice> third = store.claimProcessingBefore(
                LocalDateTime.now(), 10, "owner-2", 60000L);
        assertEquals(1, third.size());
    }

    @Test
    void findByInvoiceNoLocatesOriginalForRedApply() {
        InMemoryInvoiceStore store = new InMemoryInvoiceStore();
        Invoice invoice = invoice("I1", "B1", InvoiceState.SUCCEEDED, 0L)
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

    private Invoice invoice(String id, String biz, InvoiceState state, long version) {
        return new Invoice().setInvoiceId(id).setAppId("demo").setBizOrderNo(biz)
                .setSellerTaxNo(TAX).setState(state).setVersion(version)
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
