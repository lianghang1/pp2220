package com.sfi.conflux.auth.channels;

import com.sfi.conflux.auth.channel.AuthChannel;
import com.sfi.conflux.auth.channels.wechat.WechatMiniAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WechatMpAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WechatOpenAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WecomQrAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WecomThirdAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WecomWebAuthChannel;
import com.sfi.conflux.auth.channels.wechat.WecomWebV2AuthChannel;
import com.sfi.conflux.auth.spi.BuiltinAuthChannelRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录域内置渠道清单（编译期固化）：微信全家桶 7 种形态。
 *
 * <p>由 Java SPI 暴露给 core 的 {@code AuthGateway.Builder}，引入 channels jar 即自动注册。
 * 每构建一个 Provider 都 try-catch 容错——某渠道的可选 SDK 缺失时跳过，不影响其他渠道。
 * 后续支付宝、钉钉、飞书在此按同样方式追加。</p>
 *
 * @author lianghang
 */
public class BuiltinAuthChannelRegistrarImpl implements BuiltinAuthChannelRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BuiltinAuthChannelRegistrarImpl.class);

    @Override
    public List<AuthChannel> builtinChannels() {
        List<AuthChannel> channels = new ArrayList<AuthChannel>();

        // 微信开放平台 / 公众号 / 小程序（weixin-java-mp / miniapp）
        add(channels, WechatOpenAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WechatOpenAuthChannel(); }
        });
        add(channels, WechatMpAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WechatMpAuthChannel(); }
        });
        add(channels, WechatMiniAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WechatMiniAuthChannel(); }
        });

        // 企业微信 4 种（weixin-java-cp）
        add(channels, WecomWebAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WecomWebAuthChannel(); }
        });
        add(channels, WecomQrAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WecomQrAuthChannel(); }
        });
        add(channels, WecomWebV2AuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WecomWebV2AuthChannel(); }
        });
        add(channels, WecomThirdAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new WecomThirdAuthChannel(); }
        });

        return channels;
    }

    private static void add(List<AuthChannel> channels, String code, ChannelFactory factory) {
        try {
            channels.add(factory.create());
        } catch (Throwable t) {
            log.info("内置登录渠道[{}]不可用，跳过自动注册: {}", code, t.toString());
        }
    }

    private interface ChannelFactory {
        AuthChannel create();
    }
}
