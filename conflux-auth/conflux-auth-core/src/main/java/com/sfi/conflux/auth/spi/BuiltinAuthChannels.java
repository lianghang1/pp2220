package com.sfi.conflux.auth.spi;

import com.sfi.conflux.auth.channel.AuthChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 内置登录渠道清单访问点（core 侧）：经 Java SPI 汇总渠道模块提供的内置实现。
 *
 * <p>core 不编译依赖渠道模块（反向依赖），用 SPI 把清单注入回 core；结果缓存一次
 * （启动期装配，运行期只读）。</p>
 *
 * @author lianghang
 */
public final class BuiltinAuthChannels {

    private static volatile List<AuthChannel> cache;

    /** 所有内置渠道实现 */
    public static List<AuthChannel> all() {
        if (cache == null) {
            synchronized (BuiltinAuthChannels.class) {
                if (cache == null) {
                    cache = load();
                }
            }
        }
        return cache;
    }

    private static List<AuthChannel> load() {
        List<AuthChannel> result = new ArrayList<AuthChannel>();
        ServiceLoader<BuiltinAuthChannelRegistrar> loader =
                ServiceLoader.load(BuiltinAuthChannelRegistrar.class);
        for (BuiltinAuthChannelRegistrar registrar : loader) {
            List<AuthChannel> channels = registrar.builtinChannels();
            if (channels != null) {
                result.addAll(channels);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private BuiltinAuthChannels() {
    }
}
