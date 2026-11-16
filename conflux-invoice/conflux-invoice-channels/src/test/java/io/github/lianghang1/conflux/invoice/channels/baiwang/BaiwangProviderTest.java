package io.github.lianghang1.conflux.invoice.channels.baiwang;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceFileType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 百望云 Provider 回归：元信息、能力暴露、配置解析、缺凭据 fail-fast、fetch 降级。
 */
class BaiwangProviderTest {

    private final BaiwangChannelProvider provider = new BaiwangChannelProvider();

    @Test
    void metadata() {
        assertThat(provider.channelCode()).isEqualTo("baiwang");
        assertThat(provider.channelTier()).isEqualTo(ChannelTier.SERVICE_API);
        assertThat(provider.supportedTypes()).isNotEmpty();
    }

    @Test
    void exposesAllCapabilities() {
        assertThat(provider.issuer()).isSameAs(provider);
        assertThat(provider.querier()).isSameAs(provider);
        assertThat(provider.fileFetcher()).isSameAs(provider);
        assertThat(provider.redHandler()).isSameAs(provider);
        assertThat(provider.callbackHandler()).isSameAs(provider);
        assertThat(provider.deliverer()).isSameAs(provider);
        assertThat(provider.repairer()).isSameAs(provider);
    }

    @Test
    void parseParamsMergesAliasesAndDefaults() {
        Map<String, String> props = new LinkedHashMap<String, String>();
        props.put("clientId", "app-1");
        props.put("clientSecret", "sec-1");
        props.put("user", "admin");
        props.put("password", "pwd");
        props.put("sellerTaxNo", "338888888888SMB");
        props.put("sandbox", "true");

        BaiwangParams p = (BaiwangParams) provider.parseParams(props);
        assertThat(p.getAppKey()).isEqualTo("app-1");
        assertThat(p.getAppSecret()).isEqualTo("sec-1");
        assertThat(p.getUsername()).isEqualTo("admin");
        assertThat(p.getSellerTaxNo()).isEqualTo("338888888888SMB");
        assertThat(p.getServerUrl()).isEqualTo(BaiwangSpec.SANDBOX_BASE_URL);
        assertThat(p.channelAppId()).isEqualTo("app-1");
        assertThat(p.hasTokenSource()).isTrue();
        assertThat(p.getAttrs()).containsEntry("clientId", "app-1");
    }

    @Test
    void explicitAccessTokenCountsAsTokenSource() {
        Map<String, String> props = new LinkedHashMap<String, String>();
        props.put("appKey", "app-1");
        props.put("appSecret", "sec-1");
        props.put("accessToken", "tk-1");
        BaiwangParams p = (BaiwangParams) provider.parseParams(props);
        assertThat(p.hasTokenSource()).isTrue();
        assertThat(p.getAccessToken()).isEqualTo("tk-1");
    }

    @Test
    void issueFailsFastOnMissingCredentials() {
        InvoiceContext ctx = context(new Invoice().setBizOrderNo("B-1"), new LinkedHashMap<String, String>());
        assertThatThrownBy(() -> provider.issue(ctx))
                .isInstanceOf(GatewayException.class)
                .satisfies(e -> assertThat(((GatewayException) e).getErrorCode())
                        .isEqualTo(InvoiceErrorCode.CONFIG_MISSING));
    }

    @Test
    void fetchFallsBackToCallbackAttrsWhenNoCredentials() {
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("xmlUrl", "https://example.com/a.xml");
        attrs.put("pdfUrl", "https://example.com/a.pdf");
        Invoice invoice = new Invoice().setBizOrderNo("B-2").setAttrs(attrs);
        List<InvoiceFile> files = provider.fetch(context(invoice, Collections.<String, String>emptyMap()),
                EnumSet.of(InvoiceFileType.XML));
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getType()).isEqualTo(InvoiceFileType.XML);
    }

    private static InvoiceContext context(Invoice invoice, Map<String, String> props) {
        ChannelConfig config = new ChannelConfig();
        config.setAttrs(props);
        return InvoiceContext.of(config, null, invoice);
    }
}
