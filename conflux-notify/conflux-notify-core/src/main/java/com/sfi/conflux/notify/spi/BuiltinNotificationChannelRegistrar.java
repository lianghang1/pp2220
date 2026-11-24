package com.sfi.conflux.notify.spi;

import com.sfi.conflux.notify.channel.NotificationChannel;

import java.util.List;

/**
 * 内置通知渠道注册 SPI：渠道模块以 Java SPI 清单提供实现，core 在 build 期汇总注册。
 *
 * <p>与登录域 {@code BuiltinAuthChannelRegistrar} 同构：core 不编译依赖渠道模块，
 * 用 SPI 把内置渠道清单注入回 core。</p>
 *
 * @author lianghang
 */
public interface BuiltinNotificationChannelRegistrar {

    /** 本模块提供的内置渠道实现（可为空）。 */
    List<NotificationChannel> builtinChannels();
}
