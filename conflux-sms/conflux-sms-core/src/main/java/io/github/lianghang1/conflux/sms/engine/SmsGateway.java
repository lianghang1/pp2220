package io.github.lianghang1.conflux.sms.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

import java.util.LinkedHashMap;

/**
 * 聚合短信网关门面：统一短信 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>内部完全委托 sms4j 原始包：{@link SmsBlendRegistry} 负责配置装载，
 * 本门面只按「appId + 渠道」取出已装载的 {@link SmsBlend} 并发送，
 * 再把 sms4j 的 {@link SmsResponse} 归一为 {@link SmsSendResult}。</p>
 *
 * <p>SDK 无状态：不持有台账、不判重、不做频率控制；发送即终态。
 * 线程安全：构建后不可变，所有方法可并发调用。</p>
 *
 * @author lianghang
 */
public class SmsGateway {

    private final SmsBlendRegistry registry;

    private SmsGateway(Builder builder) {
        this.registry = builder.registry;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 装载配置（启动期/配置变更期调用）：本质是向 sms4j 注册一个 SmsBlend。
     *
     * @param appId   使用方应用标识
     * @param channel 渠道标识（sms4j supplier，如 alibaba/tencent/huawei）
     * @param attrs   配置键值（键名与 sms4j Config 字段一致）
     */
    public String register(String appId, String channel, java.util.Map<String, String> attrs) {
        return registry.register(appId, channel, attrs);
    }

    /** 补充自定义渠道 Factory（新增 sms4j 厂商时使用）。 */
    public void registerFactory(String supplier, String factoryClassName) {
        registry.registerFactory(supplier, factoryClassName);
    }

    /** 已装载的 configId 快照。 */
    public java.util.Set<String> loadedConfigIds() {
        return registry.loadedConfigIds();
    }

    /**
     * 统一发送：按 appId + 渠道取出 sms4j SmsBlend 发送，并归一结果。
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

    /** 网关构建器：仅持有装载器，零外部依赖。 */
    public static class Builder {

        private final SmsBlendRegistry registry = new SmsBlendRegistry();

        public SmsGateway build() {
            return new SmsGateway(this);
        }
    }
}
