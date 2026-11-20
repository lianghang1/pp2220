package io.github.lianghang1.conflux.pay.channels;

import io.github.lianghang1.conflux.pay.channel.BuiltinChannelRegistrar;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channels.alipay.AlipayChannelProvider;
import io.github.lianghang1.conflux.pay.channels.chinaums.ChinaumsChannelProvider;
import io.github.lianghang1.conflux.pay.channels.mock.MockChannelProvider;
import io.github.lianghang1.conflux.pay.channels.paypal.PayPalChannelProvider;
import io.github.lianghang1.conflux.pay.channels.unionpay.UnionPayChannelProvider;
import io.github.lianghang1.conflux.pay.channels.wechat.WechatChannelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付域内置渠道清单（编译期固化）：微信 / 支付宝 / 银联 / PayPal / 天满 / mock。
 *
 * <p>由 Java SPI 暴露给 core 的 {@code PaymentGateway.Builder}，实现"内置渠道全量自动注册"。
 * 每构建一个 Provider 都 try-catch 容错——某渠道的可选 SDK 未引入时跳过该渠道，
 * 不影响其他渠道注册（等价于原 starter 的 @ConditionalOnClass 语义）。</p>
 *
 * @author lianghang
 */
public class BuiltinChannelRegistrarImpl implements BuiltinChannelRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BuiltinChannelRegistrarImpl.class);

    @Override
    public List<ChannelProvider> builtinProviders() {
        List<ChannelProvider> providers = new ArrayList<ChannelProvider>();
        add(providers, "mock", new ProviderFactory() {
            public ChannelProvider create() { return new MockChannelProvider(); }
        });
        add(providers, "wechat", new ProviderFactory() {
            public ChannelProvider create() { return new WechatChannelProvider(); }
        });
        add(providers, "alipay", new ProviderFactory() {
            public ChannelProvider create() { return new AlipayChannelProvider(); }
        });
        add(providers, "unionpay", new ProviderFactory() {
            public ChannelProvider create() { return new UnionPayChannelProvider(); }
        });
        add(providers, "paypal", new ProviderFactory() {
            public ChannelProvider create() { return new PayPalChannelProvider(); }
        });
        add(providers, "chinaums", new ProviderFactory() {
            public ChannelProvider create() { return new ChinaumsChannelProvider(); }
        });
        return providers;
    }

    private static void add(List<ChannelProvider> providers, String code, ProviderFactory factory) {
        try {
            providers.add(factory.create());
        } catch (Throwable t) {
            // optional SDK 未引入 / 渠道初始化失败：跳过该渠道，不影响其他内置渠道
            log.info("内置渠道[{}]不可用，跳过自动注册: {}", code, t.toString());
        }
    }

    private interface ProviderFactory {
        ChannelProvider create();
    }
}
