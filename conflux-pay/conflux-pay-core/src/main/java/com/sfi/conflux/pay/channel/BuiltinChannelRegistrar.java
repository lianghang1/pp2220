package com.sfi.conflux.pay.channel;

import java.util.Collections;
import java.util.List;

/**
 * 内置渠道注册器 SPI：由 channels 模块实现并通过 Java SPI 声明，向 core 的
 * {@code PaymentGateway.Builder} 提供「编译期固化的内置渠道实现清单」。
 *
 * <p>为什么是 SPI：core 不能编译依赖 channels（反向依赖），但 {@code builder().build()}
 * 又要全量自动注册内置渠道。SPI 让 channels 模块把清单"注入"回 core，二者编译期解耦、
 * 运行期装配。支付域引入 channels jar 即自动注册，纯 core 场景无内置渠道（只跑自定义渠道）。</p>
 *
 * <p>实现类须对每个内置 Provider 的构建做 try-catch 容错：某渠道的 optional SDK 未引入时
 * 跳过该渠道，不影响其他内置渠道注册。</p>
 *
 * @author lianghang
 */
public interface BuiltinChannelRegistrar {

    /** 内置渠道提供者清单（不可为 null；无内置渠道返回空）。 */
    List<ChannelProvider> builtinProviders();

    /** 便捷默认：空清单。 */
    default java.util.Set<String> builtinChannelCodes() {
        List<ChannelProvider> providers = builtinProviders();
        if (providers == null || providers.isEmpty()) {
            return Collections.emptySet();
        }
        java.util.Set<String> codes = new java.util.LinkedHashSet<String>();
        for (ChannelProvider p : providers) {
            if (p != null) {
                codes.add(p.channelCode());
            }
        }
        return codes;
    }
}
