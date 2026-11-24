package com.sfi.conflux.notify.spi;

import com.sfi.conflux.notify.channel.NotificationChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 内置通知渠道清单访问点（core 侧）：经 Java SPI 汇总渠道模块提供的内置实现。
 *
 * <p>结果缓存一次（启动期装配，运行期只读）。</p>
 *
 * @author lianghang
 */
public final class BuiltinNotificationChannels {

    private static volatile List<NotificationChannel> cache;

    public static List<NotificationChannel> all() {
        if (cache == null) {
            synchronized (BuiltinNotificationChannels.class) {
                if (cache == null) {
                    cache = load();
                }
            }
        }
        return cache;
    }

    private static List<NotificationChannel> load() {
        List<NotificationChannel> result = new ArrayList<NotificationChannel>();
        ServiceLoader<BuiltinNotificationChannelRegistrar> loader =
                ServiceLoader.load(BuiltinNotificationChannelRegistrar.class);
        for (BuiltinNotificationChannelRegistrar registrar : loader) {
            List<NotificationChannel> channels = registrar.builtinChannels();
            if (channels != null) {
                result.addAll(channels);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private BuiltinNotificationChannels() {
    }
}
