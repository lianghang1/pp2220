package com.sfi.conflux.notify.engine;

import cn.hutool.core.bean.BeanUtil;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.notify.channel.SmsChannelProvider;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.config.SmsSupplierFactories;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.provider.config.SmsConfig;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.dromara.sms4j.provider.factory.BeanFactory;
import org.dromara.sms4j.provider.factory.ProviderFactoryHolder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信渠道装载器：Conflux 配置 → sms4j 官方 SmsFactory。
 *
 * <p>本类只做「配置装载」一件事：按渠道标识从 sms4j 原生注册表
 * {@link ProviderFactoryHolder} 取到 Factory，用配置键值填充出厂商 Config，交给
 * {@link SmsFactory#createSmsBlend} 注册。Factory 的注册完全走 sms4j 原生入口
 * {@code ProviderFactoryHolder.registerFactory(factory)}——内置渠道由内置清单兜底注册，
 * 自定义渠道由使用方经 {@link SmsChannelProvider#of(BaseProviderFactory)} 声明，二者同路。</p>
 *
 * <p>渠道的配置对象与发送能力全部是 sms4j 原始包的实现，本类不含任何渠道业务代码，
 * 也不依赖 sms4j 的 Spring Boot starter——纯 Java 项目同样可用。</p>
 *
 * <p>configId 采用「configId::channel」隔离：同一渠道在不同 app 下持有各自实例，
 * 避免跨 app 串配置。注册发生在启动/配置变更期，运行期只读，线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class SmsBlendRegistry {

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;

    private final Set<String> configIds = ConcurrentHashMap.newKeySet();
    /** 已声明渠道编码（与支付/发票 registeredChannels 对齐）。 */
    private final Set<String> registeredChannels = ConcurrentHashMap.newKeySet();
    /** configId → 配置指纹；配置变更时重建 SmsBlend（对齐支付/发票凭证热更新语义）。 */
    private final Map<String, String> fingerprints = new ConcurrentHashMap<String, String>();

    /**
     * 初始化 sms4j 原始包运行所需的全局线程池。
     *
     * <p>不用 starter 时不会自动初始化 BeanFactory，SmsFactory.register 内部会
     * 访问线程池。这里用 sms4j 默认 SmsConfig 建一次线程池；smsConfig 可按需覆盖。</p>
     */
    private static void ensureSms4jInitialized(SmsConfig smsConfig) {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            BeanFactory.setExecutor(smsConfig == null ? new SmsConfig() : smsConfig);
            initialized = true;
        }
    }

    /**
     * 装载一个「应用 + 渠道」配置：构造 sms4j Config 并注册进 SmsFactory。
     *
     * <p>Factory 一律从 sms4j 原生注册表 {@link ProviderFactoryHolder} 按 supplier 取；
     * 取不到时若属内置清单则兜底注册其官方 Factory（等价于官方 starter 的
     * registerDefaultFactory），清单外的自定义渠道须先经
     * {@link #register(SmsChannelProvider)} 注册。</p>
     *
     * @param configId    使用方应用标识
     * @param channel  渠道标识（sms4j supplier，如 alibaba/tencent/huawei）
     * @param attr    配置键值（键名与 sms4j Config 字段一致，如 accessKeyId/secretKey）
     * @return 注册使用的 configId（形如 configId::channel）
     */
    public String register(String configId, String channel, Map<String, String> attr) {
        if (configId == null || configId.trim().isEmpty() || channel == null || channel.trim().isEmpty()) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "configId/channel 必填");
        }
        String blendId = configId(configId, channel);
        String fingerprint = fingerprint(attr);
        // 指纹一致则复用已装载的 blend，避免每次发送都重建；配置变更则重建覆盖
        if (configIds.contains(blendId) && fingerprint.equals(fingerprints.get(blendId))) {
            return blendId;
        }
        ensureSms4jInitialized(null);

        BaseProviderFactory factory = factoryFor(channel);
        if (factory == null) {
            throw new GatewayException(NotificationErrorCode.CHANNEL_NOT_FOUND,
                    "sms4j 渠道工厂未注册: {}，请先 registerChannel 声明渠道", channel);
        }
        registeredChannels.add(channel);

        BaseConfig config = buildConfig(factory, attr);
        config.setConfigId(blendId);
        SmsFactory.createSmsBlend(config);
        configIds.add(blendId);
        fingerprints.put(blendId, fingerprint);
        log.info("短信渠道装载完成: configId={}, supplier={}", blendId, factory.getSupplier());
        return blendId;
    }

    /** 从 sms4j 原生注册表取工厂；内置渠道未注册时兜底注册其官方工厂。 */
    private BaseProviderFactory factoryFor(String channel) {
        BaseProviderFactory factory = ProviderFactoryHolder.requireForSupplier(channel);
        if (factory == null) {
            BaseProviderFactory<?, ?> builtin = SmsSupplierFactories.factoryInstance(channel);
            if (builtin != null) {
                ProviderFactoryHolder.registerFactory(builtin);
                factory = builtin;
            }
        }
        return factory;
    }

    /** 按配置对象确保装载（发送链路：从 NotificationConfigProvider 取到配置后调用）。 */
    public String ensureLoaded(NotificationConfig channelConfig) {
        if (channelConfig == null) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING, "短信渠道配置为空");
        }
        return register(channelConfig.getConfigId(), channelConfig.getChannelCode(), channelConfig.mergedAttr());
    }

    /** 配置指纹：稳定排序后拼接，键变或值变都会触发重建。 */
    private static String fingerprint(Map<String, String> attr) {
        if (attr == null || attr.isEmpty()) {
            return "";
        }
        java.util.List<String> keys = new java.util.ArrayList<String>(attr.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(key).append('=').append(attr.get(key)).append('\u0001');
        }
        return sb.toString();
    }

    /**
     * 声明渠道（与支付/发票 registerChannel(provider) 对齐）：把 sms4j 原生 Factory
     * 经官方入口注册进 ProviderFactoryHolder。内置与自定义渠道走同一条路径。
     */
    public void register(SmsChannelProvider provider) {
        if (provider == null || provider.channelCode() == null || provider.channelCode().trim().isEmpty()) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "渠道声明或 channelCode 为空");
        }
        ProviderFactoryHolder.registerFactory(provider.factory());
        registeredChannels.add(provider.channelCode());
    }

    /** 已声明/已装载的渠道编码集合（对齐支付/发票 registeredChannels）。 */
    public Set<String> registeredChannels() {
        return java.util.Collections.unmodifiableSet(registeredChannels);
    }

    /** 按 configId + 渠道取出已注册的 SmsBlend；未装载抛明确异常。 */
    public SmsBlend smsBlend(String configId, String channel) {
        String blendId = configId(configId, channel);
        SmsBlend blend = SmsFactory.getSmsBlend(blendId);
        if (blend == null) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING,
                    "短信渠道配置未装载: configId={}, channel={}", configId, channel);
        }
        return blend;
    }

    /** 已装载的 configId 快照。 */
    public Set<String> loadedConfigIds() {
        return java.util.Collections.unmodifiableSet(configIds);
    }

    /** configId 生成规则：configId::channel。 */
    public static String configId(String configId, String channel) {
        return configId + "::" + channel;
    }

    @SuppressWarnings("unchecked")
    private BaseConfig buildConfig(BaseProviderFactory factory, Map<String, String> attr) {
        try {
            BaseConfig config = (BaseConfig) factory.getConfigClass().newInstance();
            if (attr != null && !attr.isEmpty()) {
                // 忽略 Config 中不存在的键（各厂商字段差异大），大小写不敏感
                BeanUtil.fillBeanWithMapIgnoreCase(attr, config, true);
            }
            return config;
        } catch (Exception e) {
            throw new GatewayException(NotificationErrorCode.CONFIG_MISSING,
                    "无法构建短信渠道配置: " + factory.getSupplier(), e);
        }
    }
}
