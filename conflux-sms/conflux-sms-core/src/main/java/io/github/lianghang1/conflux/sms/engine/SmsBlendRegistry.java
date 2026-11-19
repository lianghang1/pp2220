package io.github.lianghang1.conflux.sms.engine;

import cn.hutool.core.bean.BeanUtil;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.channel.SmsChannelProvider;
import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsSupplierFactories;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
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
 * <p>本类只做「配置装载」一件事：按渠道标识找到 sms4j 的 Factory，用配置键值
 * 填充出厂商 Config，交给 {@link SmsFactory#createSmsBlend(SupplierConfig)} 注册。
 * 渠道的配置对象与发送能力全部是 sms4j 原始包的实现，本类不含任何渠道业务代码，
 * 也不依赖 sms4j 的 Spring Boot starter——纯 Java 项目同样可用。</p>
 *
 * <p>configId 采用「appId::channel」隔离：同一渠道在不同 app 下持有各自实例，
 * 避免跨 app 串配置。注册发生在启动/配置变更期，运行期只读，线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class SmsBlendRegistry {

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;

    private final Map<String, String> factoryOverride = new ConcurrentHashMap<String, String>();
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
     * @param appId    使用方应用标识
     * @param channel  渠道标识（sms4j supplier，如 alibaba/tencent/huawei）
     * @param attrs    配置键值（键名与 sms4j Config 字段一致，如 accessKeyId/secretKey）
     * @return 注册使用的 configId（形如 appId::channel）
     */
    public String register(String appId, String channel, Map<String, String> attrs) {
        if (appId == null || appId.trim().isEmpty() || channel == null || channel.trim().isEmpty()) {
            throw new GatewayException(SmsErrorCode.BAD_REQUEST, "appId/channel 必填");
        }
        String configId = configId(appId, channel);
        String fingerprint = fingerprint(attrs);
        // 指纹一致则复用已装载的 blend，避免每次发送都重建；配置变更则重建覆盖
        if (configIds.contains(configId) && fingerprint.equals(fingerprints.get(configId))) {
            return configId;
        }
        ensureSms4jInitialized(null);
        String factoryName = factoryOverride.containsKey(channel)
                ? factoryOverride.get(channel) : SmsSupplierFactories.factoryClassName(channel);
        if (factoryName == null) {
            throw new GatewayException(SmsErrorCode.CHANNEL_NOT_FOUND,
                    "不支持的短信渠道标识: {}", channel);
        }
        registeredChannels.add(channel);

        BaseProviderFactory factory = loadFactory(factoryName);
        // 确保 Factory 注册进 sms4j 全局表（不用 starter 时不会有自动注册）
        if (ProviderFactoryHolder.requireForSupplier(factory.getSupplier()) == null) {
            ProviderFactoryHolder.registerFactory(factory);
        }

        BaseConfig config = buildConfig(factory, attrs);
        config.setConfigId(configId);
        SmsFactory.createSmsBlend(config);
        configIds.add(configId);
        fingerprints.put(configId, fingerprint);
        log.info("短信渠道装载完成: configId={}, supplier={}", configId, factory.getSupplier());
        return configId;
    }

    /** 按配置对象确保装载（发送链路：从 SmsConfigProvider 取到配置后调用）。 */
    public String ensureLoaded(SmsChannelConfig channelConfig) {
        if (channelConfig == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING, "短信渠道配置为空");
        }
        return register(channelConfig.getAppId(), channelConfig.getChannel(), channelConfig.getAttrs());
    }

    /** 配置指纹：稳定排序后拼接，键变或值变都会触发重建。 */
    private static String fingerprint(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return "";
        }
        java.util.List<String> keys = new java.util.ArrayList<String>(attrs.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(key).append('=').append(attrs.get(key)).append('\u0001');
        }
        return sb.toString();
    }

    /**
     * 声明一个渠道（与支付/发票 registerChannel(provider) 对齐）：登记渠道编码与
     * sms4j Factory 映射。显式 factory 覆盖内置清单；未声明工厂仍可用内置映射。
     */
    public void register(SmsChannelProvider provider) {
        if (provider == null || provider.channelCode() == null || provider.channelCode().trim().isEmpty()) {
            throw new GatewayException(SmsErrorCode.BAD_REQUEST, "渠道声明或 channelCode 为空");
        }
        registeredChannels.add(provider.channelCode());
        if (provider.factoryClassName() != null) {
            factoryOverride.put(provider.channelCode(), provider.factoryClassName());
        }
    }

    /** 已声明/已装载的渠道编码集合（对齐支付/发票 registeredChannels）。 */
    public Set<String> registeredChannels() {
        return java.util.Collections.unmodifiableSet(registeredChannels);
    }

    /** 按 appId + 渠道取出已注册的 SmsBlend；未装载抛明确异常。 */
    public SmsBlend smsBlend(String appId, String channel) {
        String configId = configId(appId, channel);
        SmsBlend blend = SmsFactory.getSmsBlend(configId);
        if (blend == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "短信渠道配置未装载: appId={}, channel={}", appId, channel);
        }
        return blend;
    }

    /** 已装载的 configId 快照。 */
    public Set<String> loadedConfigIds() {
        return java.util.Collections.unmodifiableSet(configIds);
    }

    /** configId 生成规则：appId::channel。 */
    public static String configId(String appId, String channel) {
        return appId + "::" + channel;
    }

    private BaseProviderFactory loadFactory(String factoryClassName) {
        try {
            Class<?> clazz = Class.forName(factoryClassName);
            // sms4j 各厂商 Factory 均为单例：优先用官方 instance() 入口
            try {
                return (BaseProviderFactory) clazz.getMethod("instance").invoke(null);
            } catch (NoSuchMethodException ignore) {
                // 兼容无 instance() 的自定义 Factory：反射调用无参构造
                java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (BaseProviderFactory) ctor.newInstance();
            }
        } catch (Exception e) {
            throw new GatewayException(SmsErrorCode.CHANNEL_NOT_FOUND,
                    "无法装载短信渠道 Factory: " + factoryClassName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private BaseConfig buildConfig(BaseProviderFactory factory, Map<String, String> attrs) {
        try {
            BaseConfig config = (BaseConfig) factory.getConfigClass().newInstance();
            if (attrs != null && !attrs.isEmpty()) {
                // 忽略 Config 中不存在的键（各厂商字段差异大），大小写不敏感
                BeanUtil.fillBeanWithMapIgnoreCase(attrs, config, true);
            }
            return config;
        } catch (Exception e) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "无法构建短信渠道配置: " + factory.getSupplier(), e);
        }
    }
}
