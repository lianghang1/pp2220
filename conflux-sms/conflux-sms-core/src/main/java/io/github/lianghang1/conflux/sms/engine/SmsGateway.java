package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.channel.SmsSender;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;

import java.util.Set;

/**
 * 聚合短信网关门面：统一短信 API 的唯一入口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>SDK 无状态：不持有台账、不判重、不做频率控制；落库/判重/业务联动由使用方完成。
 * 短信发送即终态，无支付/发票域那样的回调状态机。</p>
 *
 * <p>线程安全：构建后不可变，所有方法可并发调用。</p>
 *
 * @author lianghang
 */
public class SmsGateway {

    private final SmsChannelRegistry registry;
    private final SmsConfigProvider configProvider;

    private SmsGateway(Builder builder) {
        this.registry = builder.registry;
        this.configProvider = builder.configProvider;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 注册渠道提供者（启动期调用）。 */
    public void registerChannel(SmsChannelProvider provider) {
        registry.register(provider);
    }

    /** 已注册渠道编码集合。 */
    public Set<String> registeredChannels() {
        return registry.registeredChannels();
    }

    /**
     * 统一发送：按 appId + 渠道取配置、找渠道发送能力、调用并归一结果。
     *
     * @param appId      使用方应用标识
     * @param channelCode 渠道编码
     * @param request    统一发送请求
     */
    public SmsSendResult send(String appId, String channelCode, SmsSendRequest request) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(channelCode) || request == null
                || StringUtils.isBlank(request.getPhone())) {
            throw new GatewayException(SmsErrorCode.BAD_REQUEST,
                    "发送参数不完整: appId/channelCode/request.phone 必填");
        }
        SmsChannelConfig config = configProvider.getConfig(appId, channelCode);
        SmsSender sender = registry.sender(channelCode);
        SmsSendResult result = sender.send(request, config);
        if (result == null) {
            throw new GatewayException(SmsErrorCode.CHANNEL_CALL_FAILED,
                    "渠道[{}]发送返回空结果", channelCode);
        }
        return result.setBizId(request.getBizId())
                .setChannel(channelCode)
                .setAppId(appId);
    }

    /** 网关构建器。 */
    public static class Builder {

        private final SmsChannelRegistry registry = new SmsChannelRegistry();
        private SmsConfigProvider configProvider;

        /** 注册渠道提供者。 */
        public Builder channel(SmsChannelProvider provider) {
            registry.register(provider);
            return this;
        }

        /** 渠道配置提供者（必填）。 */
        public Builder configProvider(SmsConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        public SmsGateway build() {
            if (configProvider == null) {
                throw new GatewayException(SmsErrorCode.CONFIG_MISSING, "未设置短信渠道配置提供者");
            }
            return new SmsGateway(this);
        }
    }
}
