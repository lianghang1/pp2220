package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.NotificationConfigProvider;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 聚合短信网关门面：统一短信 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>与支付/发票门面对齐的对外契约：{@link Builder#configProvider(NotificationConfigProvider)}
 * 注入配置源（文件/DB/配置中心均可），{@link #registerChannel(SmsChannelProvider)}
 * 声明渠道，{@link #registeredChannels()} 查看已声明渠道。发送时每次从
 * {@code NotificationConfigProvider} 读取配置，凭证变更可被装载层感知并重建客户端。</p>
 *
 * <p>内部完全委托 sms4j 原始包：{@link SmsBlendRegistry} 负责配置装载，
 * 本门面按「configId + 渠道」取出 {@link SmsBlend} 发送，把 {@link SmsResponse}
 * 归一为 {@link NotificationSendResult}。SDK 无状态：不持有台账、不判重、不做频率控制。</p>
 *
 * @author lianghang
 */
public class NotificationGateway {

    private final SmsBlendRegistry registry;
    private final NotificationConfigProvider configProvider;

    private NotificationGateway(Builder builder) {
        this.registry = builder.registry;
        this.configProvider = builder.configProvider;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 声明渠道（与支付/发票 {@code registerChannel(provider)} 对齐）：登记渠道编码
     * 的 sms4j 原生 Factory 实例。渠道实现仍全部来自 sms4j，本方法不含任何渠道逻辑。
     */
    public void registerChannel(SmsChannelProvider provider) {
        registry.register(provider);
    }

    /** 已声明渠道编码集合（对齐支付/发票 registeredChannels）。 */
    public Set<String> registeredChannels() {
        return registry.registeredChannels();
    }

    /**
     * 统一发送：按 configId + 渠道取出 sms4j SmsBlend 发送，并归一结果。
     *
     * <p>每次调用都从 {@link NotificationConfigProvider} 读取配置并确保装载
     * （凭证变更可重建）。配置源由 {@link Builder#configProvider} 注入。</p>
     *
     * @param configId       使用方应用标识
     * @param channelCode 渠道标识
     * @param request     统一发送请求
     */
    public NotificationSendResult send(String configId, String channelCode, NotificationSendRequest request) {
        if (StringUtils.isBlank(configId) || StringUtils.isBlank(channelCode) || request == null
                || StringUtils.isBlank(request.getPhone())) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "发送参数不完整: configId/channelCode/request.phone 必填");
        }
        NotificationConfig cfg = configProvider.getConfig(configId, channelCode);
        registry.ensureLoaded(cfg);
        SmsBlend blend = registry.smsBlend(configId, channelCode);
        SmsResponse response = dispatch(blend, request);
        boolean ok = response != null && response.isSuccess();
        return new NotificationSendResult()
                .setSuccess(ok)
                .setBizId(request.getBizId())
                .setChannel(channelCode)
                .setConfigId(configId)
                .setRaw(response == null ? null : response.getData())
                .setError(ok ? null : (response == null ? "渠道返回空结果" : String.valueOf(response.getData())));
    }

    /** 请求参数 → sms4j 发送重载：有模板变量走 Map 重载，否则走文本重载。 */
    private SmsResponse dispatch(SmsBlend blend, NotificationSendRequest request) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            return blend.sendMessage(request.getPhone(),
                    new LinkedHashMap<String, String>(request.getParams()));
        }
        return blend.sendMessage(request.getPhone(), request.getContent());
    }

    /** 网关构建器：持有装载器与配置提供者，零外部框架依赖。 */
    public static class Builder {

        private final SmsBlendRegistry registry = new SmsBlendRegistry();
        private NotificationConfigProvider configProvider;

        /** 声明渠道（对齐支付/发票同名方法），可多次调用。 */
        public Builder channel(SmsChannelProvider provider) {
            registry.register(provider);
            return this;
        }

        /**
         * 渠道配置提供者（必填，对齐支付/发票同名方法）：发送链路每次从中读取配置。
         * 若实现 {@link NotificationConfigProvider#allConfigs()}，构建时按全部配置预热装载、坏配置 fail-fast。
         */
        public Builder configProvider(NotificationConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** 构建；渠道配置提供者必填（对齐支付/发票）。 */
        public NotificationGateway build() {
            if (configProvider == null) {
                throw new GatewayException(NotificationErrorCode.CONFIG_MISSING, "未设置渠道配置提供者");
            }
            for (NotificationConfig cfg : configProvider.allConfigs()) {
                registry.ensureLoaded(cfg);
            }
            return new NotificationGateway(this);
        }
    }
}
