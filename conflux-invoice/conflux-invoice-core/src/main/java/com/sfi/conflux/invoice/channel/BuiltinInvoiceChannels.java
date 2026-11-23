package com.sfi.conflux.invoice.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 内置发票渠道清单访问点（core 侧）：经 Java SPI 汇总 channels 模块提供的内置实现。
 *
 * <p>core 不编译依赖 channels（反向依赖），用 SPI 把清单注入回 core；结果缓存一次
 * （启动期装配，运行期只读）。</p>
 *
 * @author lianghang
 */
public final class BuiltinInvoiceChannels {

    private static volatile List<InvoiceChannelProvider> cache;

    /** 所有内置渠道实现。 */
    public static List<InvoiceChannelProvider> all() {
        if (cache == null) {
            synchronized (BuiltinInvoiceChannels.class) {
                if (cache == null) {
                    cache = load();
                }
            }
        }
        return cache;
    }

    private static List<InvoiceChannelProvider> load() {
        List<InvoiceChannelProvider> result = new ArrayList<InvoiceChannelProvider>();
        ServiceLoader<BuiltinInvoiceChannelRegistrar> loader =
                ServiceLoader.load(BuiltinInvoiceChannelRegistrar.class);
        for (BuiltinInvoiceChannelRegistrar registrar : loader) {
            List<InvoiceChannelProvider> providers = registrar.builtinProviders();
            if (providers != null) {
                result.addAll(providers);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private BuiltinInvoiceChannels() {
    }
}
