package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;
import com.sfi.conflux.invoice.model.ChannelTier;
import com.sfi.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渠道注册表：能力缺失与票种不支持必须报明确错误，而不是空指针。 */
class InvoiceChannelRegistryTest {

    @Test
    void unknownChannelThrowsChannelNotFound() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        GatewayException ex = assertThrows(GatewayException.class,
                () -> registry.provider("unknown"));
        assertSame(InvoiceErrorCode.CHANNEL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void missingCapabilityThrowsCapabilityNotSupported() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new StubProvider("stub"));
        GatewayException ex = assertThrows(GatewayException.class, () -> registry.issuer("stub"));
        assertSame(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    void unsupportedInvoiceTypeIsRejectedBeforeChannelCall() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new StubProvider("stub"));
        registry.assertTypeSupported("stub", InvoiceType.DIGITAL_VAT_SPECIAL);
        GatewayException ex = assertThrows(GatewayException.class,
                () -> registry.assertTypeSupported("stub", InvoiceType.VAT_GENERAL));
        assertSame(InvoiceErrorCode.INVOICE_TYPE_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    void duplicateRegistrationKeepsFirstProvider() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        StubProvider first = new StubProvider("stub");
        registry.register(first);
        registry.register(new StubProvider("stub"));
        assertSame(first, registry.provider("stub"));
        assertEquals(1, registry.registeredChannels().size());
    }

    @Test
    void registeredChannelsAreExposed() {
        InvoiceChannelRegistry registry = new InvoiceChannelRegistry();
        registry.register(new StubProvider("a"));
        registry.register(new StubProvider("b"));
        assertTrue(registry.registeredChannels().contains("a"));
        assertTrue(registry.registeredChannels().contains("b"));
        assertEquals(ChannelTier.SERVICE_API, registry.tier("a"));
    }

    /** 无任何能力的桩渠道，用于验证缺失能力的报错路径。 */
    private static final class StubProvider implements InvoiceChannelProvider {
        private final String code;

        StubProvider(String code) {
            this.code = code;
        }

        @Override
        public String channelCode() {
            return code;
        }

        @Override
        public ChannelTier channelTier() {
            return ChannelTier.SERVICE_API;
        }

        @Override
        public Set<InvoiceType> supportedTypes() {
            return EnumSet.of(InvoiceType.DIGITAL_VAT_SPECIAL);
        }
    }
}
