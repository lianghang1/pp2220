package com.sfi.conflux.invoice.autoconfigure;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channel.ChannelInstance;
import com.sfi.conflux.invoice.channel.InvoiceChannelProvider;
import com.sfi.conflux.invoice.channels.mock.MockInvoiceChannelProvider;
import com.sfi.conflux.invoice.config.InvoiceConfigProvider;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;
import com.sfi.conflux.invoice.model.ChannelTier;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配置提供者：configId+渠道+税号三维索引、通道分级与跳过策略。 */
class PropertiesInvoiceConfigProviderTest {

    private static final String TAX = "91310000TEST000001";

    @Test
    void indexesConfigChannelTaxNo() {
        InvoiceConfigProvider provider = build(singleApp("demo", "app-1", TAX, "AUTH"));
        ChannelConfig config = provider.getConfig("demo", "mock", TAX);
        assertEquals("demo", config.getConfigId());
        assertEquals(TAX, config.getSellerTaxNo());
        List<ChannelInstance> instances = provider.instances("demo", TAX);
        assertEquals(1, instances.size());
        assertSame(ChannelTier.SERVICE_API, instances.get(0).getTier());
    }

    @Test
    void differentConfigsWithSameChannelAppKeyAllowed() {
        InvoiceProperties props = new InvoiceProperties();
        props.getConfigs().put("demo", app("app-1", TAX));
        props.getConfigs().put("other", app("app-1", "91310000TEST000002"));
        InvoiceConfigProvider provider = new PropertiesInvoiceConfigProvider(props, providers());
        assertEquals("demo", provider.getConfig("demo", "mock", TAX).getConfigId());
        assertEquals("other", provider.getConfig("other", "mock", "91310000TEST000002").getConfigId());
    }

    @Test
    void unregisteredChannelIsSkipped() {
        InvoiceProperties props = singleApp("demo", "app-1", TAX, "AUTH");
        props.getConfigs().get("demo").put("unknown", channel("app-2", TAX));
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
        InvoiceProperties.ChannelProperties channel = props.getConfigs().get("demo").get("mock");
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

    private InvoiceProperties singleApp(String configId, String appKey, String taxNo, String outcome) {
        InvoiceProperties props = new InvoiceProperties();
        props.getConfigs().put(configId, app(appKey, taxNo));
        props.getConfigs().get(configId).get("mock").getAttr().put("issueOutcome", outcome);
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
        channel.getAttr().put("appKey", appKey);
        channel.getAttr().put("appSecret", "secret");
        return channel;
    }
}
