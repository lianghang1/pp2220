package com.sfi.conflux.auth.channels;

import com.sfi.conflux.auth.channel.AuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliAppAuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliH5AuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliH5InAppAuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliH5JsapiAuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliMiniAuthChannel;
import com.sfi.conflux.auth.channels.alipay.AliWebAuthChannel;
import com.sfi.conflux.auth.channels.feishu.FeishuAppAuthChannel;
import com.sfi.conflux.auth.channels.feishu.FeishuInAppAuthChannel;
import com.sfi.conflux.auth.channels.feishu.FeishuMiniAuthChannel;
import com.sfi.conflux.auth.channels.feishu.FeishuQrAuthChannel;
import com.sfi.conflux.auth.channels.feishu.FeishuWebAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingAdminAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingH5InternalAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingH5IsvAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingMiniAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingQrAuthChannel;
import com.sfi.conflux.auth.channels.dingtalk.DingWebAuthChannel;
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
 * 登录域内置渠道清单（编译期固化）：微信 7 + 钉钉 6 + 飞书 5 + 支付宝 6 = 24 种形态。
 *
 * <p>由 Java SPI 暴露给 core 的 {@code AuthGateway.Builder}，引入 channels jar 即自动注册。
 * 每构建一个 Provider 都 try-catch 容错——某渠道的可选 SDK 缺失时跳过，不影响其他渠道。</p>
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

        // 钉钉全家桶 6 种（标准 OAuth2 + 容器内免登薄适配，无第三方 SDK）
        add(channels, DingQrAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingQrAuthChannel(); }
        });
        add(channels, DingWebAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingWebAuthChannel(); }
        });
        add(channels, DingH5InternalAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingH5InternalAuthChannel(); }
        });
        add(channels, DingH5IsvAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingH5IsvAuthChannel(); }
        });
        add(channels, DingMiniAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingMiniAuthChannel(); }
        });
        add(channels, DingAdminAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new DingAdminAuthChannel(); }
        });

        // 飞书全家桶 5 种（标准 OAuth2 授权码 + 小程序 code2session 薄适配，无第三方 SDK）
        add(channels, FeishuWebAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new FeishuWebAuthChannel(); }
        });
        add(channels, FeishuQrAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new FeishuQrAuthChannel(); }
        });
        add(channels, FeishuAppAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new FeishuAppAuthChannel(); }
        });
        add(channels, FeishuInAppAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new FeishuInAppAuthChannel(); }
        });
        add(channels, FeishuMiniAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new FeishuMiniAuthChannel(); }
        });

        // 支付宝全家桶 6 种（官方 alipay-sdk-java，RSA2 公钥/证书双模式）
        add(channels, AliWebAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliWebAuthChannel(); }
        });
        add(channels, AliH5AuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliH5AuthChannel(); }
        });
        add(channels, AliH5JsapiAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliH5JsapiAuthChannel(); }
        });
        add(channels, AliH5InAppAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliH5InAppAuthChannel(); }
        });
        add(channels, AliAppAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliAppAuthChannel(); }
        });
        add(channels, AliMiniAuthChannel.CHANNEL_CODE, new ChannelFactory() {
            public AuthChannel create() { return new AliMiniAuthChannel(); }
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
