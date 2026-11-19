package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 聚合短信网关门面：统一短信 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>与支付/发票门面对齐的对外契约：{@link Builder#configProvider(SmsConfigProvider)}
 * 注入配置源（文件/DB/配置中心均可），{@link #registerChannel(String, String, Map)}
 * 装载渠道，{@link #registeredChannels()} 查看已装载渠道。发送时每次从
 * {@code SmsConfigProvider} 读取配置，凭证变更可被装载层感知并重建客户端。</p>
 *
 * <p>内部完全委托 sms4j 原始包：{@link SmsBlendRegistry} 负责配置装载，
 * 本门面按「appId + 渠道」取出 {@link SmsBlend} 发送，把 {@link SmsResponse}
 * 归一为 {@link SmsSendResult}。SDK 无状态：不持有台账、不判重、不做频率控制。</p>
 *
 * @author lianghang
 */
public class SmsGateway {

    private final SmsBlendRegistry registry;
    private final SmsConfigProvider configProvider;

    private SmsGateway(Builder builder) {
        this.registry = builder.registry;
        this.configProvider = builder.configProvider;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 装载配置渠道（标准命名，与支付/发票 {@code registerChannel} 对齐）：
     * 本质是向 sms4j 注册一个 SmsBlend。
     *
     * @param appId   使用方应用标识
     * @param channel 渠道标识（sms4j supplier，如 alibaba/tencent/huawei）
     * @param attrs   配置键值（键名与 sms4j Config 字段一致）
     * @return 注册使用的 configId
     */
    public String registerChannel(String appId, String channel, Map<String, String> attrs) {
        return registry.register(appId, channel, attrs);
    }

    /**
     * 装载配置（{@link #registerChannel} 的兼容别名）。
     */
    public String register(String appId, String channel, Map<String, String> attrs) {
        return registerChannel(appId, channel, attrs);
    }

    /** 补充自定义渠道 Factory（新增 sms4j 厂商时使用）。 */
    public void registerFactory(String supplier, String factoryClassName) {
        registry.registerFactory(supplier, factoryClassName);
    }

    /** 已装载渠道的 configId 快照（标准命名，对齐支付/发票 registeredChannels）。 */
    public Set<String> registeredChannels() {
        return registry.loadedConfigIds();
    }

    /** 已装载的 configId 快照（兼容别名）。 */
    public Set<String> loadedConfigIds() {
        return registeredChannels();
    }

    /**
     * 统一发送：按 appId + 渠道取出 sms4j SmsBlend 发送，并归一结果。
     *
     * <p>若注入了 {@link SmsConfigProvider}，每次调用都从中读取配置并确保装载
     * （凭证变更可重建）；未注入时直接使用 {@link #registerChannel} 预装载的 blend。</p>
     *
     * @param appId       使用方应用标识
     * @param channelCode 渠道标识
     * @param request     统一发送请求
     */
    public SmsSendResult send(String appId, String channelCode, SmsSendRequest request) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(channelCode) || request == null
                || StringUtils.isBlank(request.getPhone())) {
            throw new GatewayException(SmsErrorCode.BAD_REQUEST,
                    "发送参数不完整: appId/channelCode/request.phone 必填");
        }
        if (configProvider != null) {
            SmsChannelConfig cfg = configProvider.getConfig(appId, channelCode);
            registry.ensureLoaded(cfg);
        }
        SmsBlend blend = registry.smsBlend(appId, channelCode);
        SmsResponse response = dispatch(blend, request);
        boolean ok = response != null && response.isSuccess();
        return new SmsSendResult()
                .setSuccess(ok)
                .setBizId(request.getBizId())
                .setChannel(channelCode)
                .setAppId(appId)
                .setRaw(response == null ? null : response.getData())
                .setError(ok ? null : (response == null ? "渠道返回空结果" : String.valueOf(response.getData())));
    }

    /** 请求参数 → sms4j 发送重载：有模板变量走 Map 重载，否则走文本重载。 */
    private SmsResponse dispatch(SmsBlend blend, SmsSendRequest request) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            return blend.sendMessage(request.getPhone(),
                    new LinkedHashMap<String, String>(request.getParams()));
        }
        return blend.sendMessage(request.getPhone(), request.getContent());
    }

    /** 网关构建器：持有装载器与可选配置提供者，零外部框架依赖。 */
    public static class Builder {

        private final SmsBlendRegistry registry = new SmsBlendRegistry();
        private SmsConfigProvider configProvider;

        /**
         * 渠道配置提供者（可选，对齐支付/发票同名方法）：注入后发送链路每次从中读取配置。
         * 未注入时可用 {@link SmsGateway#registerChannel} 预装载。
         */
        public Builder configProvider(SmsConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        public SmsGateway build() {
            return new SmsGateway(this);
        }
    }
}
