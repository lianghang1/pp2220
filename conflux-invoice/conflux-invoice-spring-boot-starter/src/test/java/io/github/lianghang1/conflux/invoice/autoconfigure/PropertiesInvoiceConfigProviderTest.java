package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配置提供者：三维索引、账户键唯一性 fail-fast、通道分级与跳过策略。 */
class PropertiesInvoiceConfigProviderTest {

    private static final String TAX = "91310000TEST000001";

    @Test
    void indexesAppChannelTaxNoAndAccountKey() {
        InvoiceConfigProvider provider = build(singleApp("demo", "app-1", TAX, "AUTH"));
        ChannelConfig config = provider.getConfig("demo", "mock", TAX);
        assertEquals("app-1#" + TAX, config.getAccountKey());
        assertEquals("demo", provider.getByAccountKey("mock", "app-1#" + TAX).getAppId());
        List<ChannelInstance> instances = provider.instances("demo", TAX);
        assertEquals(1, instances.size());
        assertSame(ChannelTier.SERVICE_API, instances.get(0).getTier());
    }

    @Test
    void duplicateAccountKeyFailsFastAtStartup() {
        InvoiceProperties props = new InvoiceProperties();
        props.getApps().put("demo", app("app-1", TAX));
        props.getApps().put("other", app("app-1", TAX));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> new PropertiesInvoiceConfigProvider(props, providers()));
        assertSame(InvoiceErrorCode.CONFIG_CONFLICT, ex.getErrorCode());
    }

    @Test
    void differentTaxNoUnderSameAppKeyIsAllowed() {
        InvoiceProperties props = new InvoiceProperties();
        props.getApps().put("demo", app("app-1", TAX));
        props.getApps().put("other", app("app-1", "91310000TEST000002"));
        InvoiceConfigProvider provider = new PropertiesInvoiceConfigProvider(props, providers());
        assertEquals("demo", provider.getByAccountKey("mock", "app-1#" + TAX).getAppId());
        assertEquals("other", provider.getByAccountKey("mock",
                "app-1#91310000TEST000002").getAppId());
    }

    @Test
    void unregisteredChannelIsSkipped() {
        InvoiceProperties props = singleApp("demo", "app-1", TAX, "AUTH");
        props.getApps().get("demo").put("unknown", channel("app-2", TAX));
        InvoiceConfigProvider provider = new PropertiesInvoiceConfigProvider(props, providers());
        assertEquals(1, provider.instances("demo", TAX).size());
    }

    @Test
    void missingConfigThrowsConfigMissing() {
        InvoiceConfigProvider provider = build(singleApp("demo", "app-1", TAX, "AUTH"));
        GatewayException ex = assertThrows(GatewayException.class,
                () -> provider.getConfig("demo", "mock", "NOT-EXIST"));
        assertSame(InvoiceErrorCode.CONFIG_MISSING, ex.getErrorCode());
    }

    @Test
    void tierAndPriorityAreBound() {
        InvoiceProperties props = singleApp("demo", "app-1", TAX, "AUTH");
        InvoiceProperties.ChannelProperties channel = props.getApps().get("demo").get("mock");
        channel.setTier("DIRECT_TAX");
        channel.setPriority(50);
        InvoiceConfigProvider provider = new PropertiesInvoiceConfigProvider(props, providers());
        ChannelInstance instance = provider.instances("demo", TAX).get(0);
        assertSame(ChannelTier.DIRECT_TAX, instance.getTier());
        assertEquals(300050, instance.routingScore());
        assertTrue(instance.isEnabled());
    }

    private InvoiceConfigProvider build(InvoiceProperties props) {
        return new PropertiesInvoiceConfigProvider(props, providers());
    }

    private List<InvoiceChannelProvider> providers() {
        return Collections.<InvoiceChannelProvider>singletonList(new MockInvoiceChannelProvider());
    }

    private InvoiceProperties singleApp(String appId, String appKey, String taxNo, String outcome) {
        InvoiceProperties props = new InvoiceProperties();
        props.getApps().put(appId, app(appKey, taxNo));
        props.getApps().get(appId).get("mock").getAttrs().put("issueOutcome", outcome);
        return props;
    }

    private Map<String, InvoiceProperties.ChannelProperties> app(String appKey, String taxNo) {
        Map<String, InvoiceProperties.ChannelProperties> channels =
                new LinkedHashMap<String, InvoiceProperties.ChannelProperties>();
        channels.put("mock", channel(appKey, taxNo));
        return channels;
    }

    private InvoiceProperties.ChannelProperties channel(String appKey, String taxNo) {
        InvoiceProperties.ChannelProperties channel = new InvoiceProperties.ChannelProperties();
        channel.setSellerTaxNo(taxNo);
        channel.getAttrs().put("appKey", appKey);
        channel.getAttrs().put("appSecret", "secret");
        return channel;
    }
}
