package com.sfi.conflux.auth.spi;

import com.sfi.conflux.auth.channel.AuthChannel;

import java.util.List;

/**
 * 内置登录渠道注册器 SPI：由渠道模块实现并通过 Java SPI 声明，向 core 的
 * {@code AuthGateway.Builder} 提供「编译期固化的内置渠道实现清单」。
 *
 * <p>core 不编译依赖渠道实现模块（反向依赖），故用 SPI 注入清单：引入渠道 jar 即
 * 自动注册内置渠道，只用 core 时仅含自定义渠道。实现类须对每个内置 Provider 的构建
 * 做 try-catch 容错（可选 SDK 缺失时跳过该渠道）。</p>
 *
 * @author lianghang
 */
public interface BuiltinAuthChannelRegistrar {

    /** 内置渠道提供者清单（不可为 null） */
    List<AuthChannel> builtinChannels();
}
