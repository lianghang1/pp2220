package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.NotificationChannel;
import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.exception.NotificationErrorCode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聚合通知网关门面：统一消息发送 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>与登录域 {@code AuthGateway} 同构的「门面只路由」范式：内部一张
 * {@code Map<channelCode, NotificationChannel>} 渠道表，{@link #send} 只做三件事——
 * 按 configId + 消息自带 channelCode 读配置、找到渠道、转发。渠道实现（短信 sms4j / 微信等）
 * 各自封装 SDK 调用、参数校验与结果归一，新增渠道不改门面 API。</p>
 *
 * <p>与支付/发票门面对齐的对外契约：{@link Builder#configProvider(NotificationConfigProvider)}
 * 注入配置源，{@link #registerChannel(NotificationChannel)} 声明渠道，
 * {@link #registeredChannels()} 查看已装载渠道。每次发送都从配置源读取，凭证变更可被装载层感知并重建。</p>
 *
 * <p>短信渠道由 {@link Sms4jChannel} 委托 sms4j 原始包：{@link SmsBlendRegistry} 负责配置装载，
 * 指纹缓存与 configId::channel 隔离全部保留在原装载器内部，零回归。SDK 无状态：不持有台账、不判重、不做频率控制。</p>
 *
 * @author lianghang
 */
public class NotificationGateway {

    private final Map<String, NotificationChannel> channels;
    private final NotificationConfigProvider configProvider;
    private final SmsBlendRegistry registry;

    private NotificationGateway(Builder builder) {
        this.channels = builder.channels;
        this.configProvider = builder.configProvider;
        this.registry = builder.registry;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 声明自定义通知渠道（与登录域 {@code registerChannel(AuthChannel)} 对齐）。
     * 内置渠道由 build() 自动注册，本方法仅供扩展新平台。
     */
    public void registerChannel(NotificationChannel channel) {
        requireValidChannel(channel);
        channels.put(channel.channelCode(), channel);
    }

    /**
     * 声明 sms4j 原生渠道工厂（兼容原有短信扩展口）：把 Factory 注册进 sms4j 原生注册表，
     * 并登记一个短信渠道到统一渠道表。内置与自定义短信渠道走同一条路径。
     */
    public void registerChannel(SmsChannelProvider provider) {
        registry.register(provider);
        channels.put(provider.channelCode(), new Sms4jChannel(provider.channelCode(), registry));
    }

    /** 已装载渠道编码集合（对齐支付/发票 registeredChannels；短信侧沿用装载器已装载集合）。 */
    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(registry.registeredChannels()));
    }

    /**
     * 统一发送（唯一入口）：按 configId + 消息自带 channelCode 读配置 → 找渠道 → 转发，归一结果。
     *
     * <p>所有消息形态（短信、微信全家桶、未来 IM）一律以 {@link NotificationMessage} 入参，
     * 渠道编码由消息自带——消息决定「用哪个平台凭证」，configId 决定「用该平台哪份配置」。
     * 门面只做路由：参数校验下沉到各渠道（如短信校验 phone），门面只守 configId / message / channelCode 非空。</p>
     *
     * @param configId 使用方应用标识
     * @param message  统一通知消息（自带 channelCode）
     */
    public NotificationSendResult send(String configId, NotificationMessage message) {
        if (StringUtils.isBlank(configId) || message == null || StringUtils.isBlank(message.channelCode())) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "发送参数不完整: configId/message/message.channelCode 必填");
        }
        String channelCode = message.channelCode();
        // 先读配置（契约：每次 send 都读 provider；缺失即 fail-fast）
        NotificationConfig cfg = configProvider.getConfig(configId, channelCode);
        if (cfg == null) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        NotificationChannel channel = channels.get(channelCode);
        if (channel == null) {
            // 未在表中声明的渠道：若属 sms4j 内置供应商则懒装载短信渠道（配置驱动，兼容原有行为）；
            // 否则交由装载器抛 CHANNEL_NOT_FOUND。
            channel = new Sms4jChannel(channelCode, registry);
            channels.put(channelCode, channel);
        }
        return channel.send(cfg, message);
    }

    private static void requireValidChannel(NotificationChannel channel) {
        if (channel == null || StringUtils.isBlank(channel.channelCode())) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "渠道或 channelCode 不能为空");
        }
    }

    /** 网关构建器：持有渠道表、装载器与配置源，零外部框架依赖。 */
    public static class Builder {

        private final SmsBlendRegistry registry = new SmsBlendRegistry();
        private final Map<String, NotificationChannel> channels = new ConcurrentHashMap<String, NotificationChannel>();
        private NotificationConfigProvider configProvider;

        /** 声明自定义通知渠道（如未来的 IM 平台），可多次调用。 */
        public Builder channel(NotificationChannel channel) {
            requireValid(channel);
            channels.put(channel.channelCode(), channel);
            return this;
        }

        /** 声明 sms4j 原生渠道工厂（兼容原有短信扩展口），可多次调用。 */
        public Builder channel(SmsChannelProvider provider) {
            if (provider == null) {
                throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "渠道声明不能为空");
            }
            registry.register(provider);
            channels.put(provider.channelCode(), new Sms4jChannel(provider.channelCode(), registry));
            return this;
        }

        /** 渠道配置提供者（必填，对齐支付/发票同名方法）。 */
        public Builder configProvider(NotificationConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** 构建；渠道配置提供者必填（对齐支付/发票）。 */
        public NotificationGateway build() {
            if (configProvider == null) {
                throw new GatewayException(NotificationErrorCode.CONFIG_MISSING, "未设置渠道配置提供者");
            }
            // 按全部配置预热装载短信渠道（坏配置 fail-fast），并把已配置渠道登记进统一渠道表
            for (NotificationConfig cfg : configProvider.allConfigs()) {
                registry.ensureLoaded(cfg);
                String code = cfg.getChannelCode();
                if (!channels.containsKey(code)) {
                    channels.put(code, new Sms4jChannel(code, registry));
                }
            }
            return new NotificationGateway(this);
        }

        private static void requireValid(NotificationChannel channel) {
            if (channel == null || StringUtils.isBlank(channel.channelCode())) {
                throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "渠道或 channelCode 不能为空");
            }
        }
    }
}
