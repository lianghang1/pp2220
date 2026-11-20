package io.github.lianghang1.conflux.invoice.channels;

import io.github.lianghang1.conflux.invoice.channel.BuiltinInvoiceChannelRegistrar;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.baiwang.BaiwangChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.mock.MockInvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.nuonuo.NuonuoChannelProvider;
import io.github.lianghang1.conflux.invoice.channels.piaotong.PiaoTongChannelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 发票域内置渠道清单（编译期固化）：百望、百望云、票通、诺诺、mock。
 *
 * <p>由 Java SPI 暴露给 core 的 {@code InvoiceGateway.Builder}，实现内置渠道全量自动注册。
 * 每构建一个 Provider 都 try-catch 容错——某渠道的可选依赖缺失时跳过，不影响其他渠道。</p>
 *
 * @author lianghang
 */
public class BuiltinInvoiceChannelRegistrarImpl implements BuiltinInvoiceChannelRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BuiltinInvoiceChannelRegistrarImpl.class);

    @Override
    public List<InvoiceChannelProvider> builtinProviders() {
        List<InvoiceChannelProvider> providers = new ArrayList<InvoiceChannelProvider>();
        add(providers, "mock", new ProviderFactory() {
            public InvoiceChannelProvider create() { return new MockInvoiceChannelProvider(); }
        });
        add(providers, "baiwang", new ProviderFactory() {
            public InvoiceChannelProvider create() { return new BaiwangChannelProvider(); }
        });
        add(providers, "baiwangjs", new ProviderFactory() {
            public InvoiceChannelProvider create() { return new BaiwangJsChannelProvider(); }
        });
        add(providers, "piaotong", new ProviderFactory() {
            public InvoiceChannelProvider create() { return new PiaoTongChannelProvider(); }
        });
        add(providers, "nuonuo", new ProviderFactory() {
            public InvoiceChannelProvider create() { return new NuonuoChannelProvider(); }
        });
        return providers;
    }

    private static void add(List<InvoiceChannelProvider> providers, String code, ProviderFactory factory) {
        try {
            providers.add(factory.create());
        } catch (Throwable t) {
            log.info("内置发票渠道[{}]不可用，跳过自动注册: {}", code, t.toString());
        }
    }

    private interface ProviderFactory {
        InvoiceChannelProvider create();
    }
}
