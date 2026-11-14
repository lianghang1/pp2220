package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 账户键：配置侧生成与回调侧解析必须使用同一套规则。 */
class InvoiceAccountKeyTest {

    @Test
    void singleAccountKeepsChannelAppIdUnchanged() {
        assertEquals("app-1", InvoiceAccountKey.of("app-1", null));
        assertEquals("app-1", InvoiceAccountKey.of("app-1", "  "));
        assertFalse(InvoiceAccountKey.isComposite("app-1"));
        assertNull(InvoiceAccountKey.sellerTaxNoOf("app-1"));
    }

    @Test
    void multiTaxNoAppendsSeparator() {
        String key = InvoiceAccountKey.of("app-1", " 91310000X ");
        assertEquals("app-1#91310000X", key);
        assertTrue(InvoiceAccountKey.isComposite(key));
        assertEquals("91310000X", InvoiceAccountKey.sellerTaxNoOf(key));
    }

    @Test
    void blankChannelAppIdProducesNoKey() {
        assertNull(InvoiceAccountKey.of(null, "91310000X"));
        assertNull(InvoiceAccountKey.of("", "91310000X"));
    }

    @Test
    void resolvePrefersRoutingParamOnQueryThenForm() {
        Map<String, String> query = new HashMap<String, String>();
        query.put(InvoiceAccountKey.ROUTING_PARAM, "app-1#TAX-A");
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest().setQueryParams(query);
        assertEquals("app-1#TAX-A", InvoiceAccountKey.resolve(raw, "legacy"));

        Map<String, String> form = new HashMap<String, String>();
        form.put(InvoiceAccountKey.ROUTING_PARAM, "app-1#TAX-B");
        raw.setQueryParams(new HashMap<String, String>()).setFormParams(form);
        assertEquals("app-1#TAX-B", InvoiceAccountKey.resolve(raw, "legacy"));
    }

    @Test
    void resolveFallsBackToLegacyValue() {
        InvoiceCallbackRequest raw = new InvoiceCallbackRequest();
        assertEquals("legacy", InvoiceAccountKey.resolve(raw, "legacy"));
        assertNull(InvoiceAccountKey.resolve(null, null));
    }
}
