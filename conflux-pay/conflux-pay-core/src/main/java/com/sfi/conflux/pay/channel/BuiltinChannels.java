package com.sfi.conflux.pay.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 内置渠道清单访问点（core 侧）：经 Java SPI 汇总 channels 模块提供的内置渠道实现。
 *
 * <p>core 不编译依赖 channels（反向依赖），故用 SPI 把 channels 的"编译期固化能力清单"
 * 注入回 core。引入 channels jar 即自动发现内置渠道，只用 core 时无内置渠道（仅自定义）。
 * 结果缓存一次（启动期装配，运行期只读）。</p>
 *
 * @author lianghang
 */
public final class BuiltinChannels {

    private static volatile List<ChannelProvider> cache;

    /** 所有内置渠道实现（各 registrar 汇总，去重按 channelCode）。 */
    public static List<ChannelProvider> all() {
        if (cache == null) {
            synchronized (BuiltinChannels.class) {
                if (cache == null) {
                    cache = load();
                }
            }
        }
        return cache;
    }

    private static List<ChannelProvider> load() {
        List<ChannelProvider> result = new ArrayList<ChannelProvider>();
        ServiceLoader<BuiltinChannelRegistrar> loader = ServiceLoader.load(BuiltinChannelRegistrar.class);
        for (BuiltinChannelRegistrar registrar : loader) {
            List<ChannelProvider> providers = registrar.builtinProviders();
            if (providers != null) {
                result.addAll(providers);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 清空缓存（测试用）。 */
    static void reset() {
        cache = null;
    }

    private BuiltinChannels() {
    }
}
