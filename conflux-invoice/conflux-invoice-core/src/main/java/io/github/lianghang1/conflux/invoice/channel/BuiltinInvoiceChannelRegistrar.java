package io.github.lianghang1.conflux.invoice.channel;

import java.util.List;

/**
 * 内置发票渠道注册器 SPI：由 channels 模块实现并通过 Java SPI 声明，向 core 的
 * {@code InvoiceGateway.Builder} 提供「编译期固化的内置渠道实现清单」。
 *
 * <p>core 不能编译依赖 channels（反向依赖），故用 SPI 注入清单：引入 channels jar 即
 * 自动注册内置渠道，只用 core 时仅含自定义渠道。实现类须对每个内置 Provider 的构建
 * 做 try-catch 容错（可选 SDK 缺失时跳过该渠道）。</p>
 *
 * @author lianghang
 */
public interface BuiltinInvoiceChannelRegistrar {

    /** 内置渠道提供者清单（不可为 null）。 */
    List<InvoiceChannelProvider> builtinProviders();
}
