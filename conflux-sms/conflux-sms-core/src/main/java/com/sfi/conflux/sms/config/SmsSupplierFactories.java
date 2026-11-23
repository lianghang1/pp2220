package com.sfi.conflux.sms.config;

import org.dromara.sms4j.provider.factory.BaseProviderFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * sms4j 内置渠道标识 → 官方 Factory 实例（装载元数据）。
 *
 * <p>官方 starter 通过 {@code SmsBlendsInitializer#registerDefaultFactory()} 把内置
 * 厂商的 {@code XxxFactory.instance()} 逐个注册进 sms4j。本项目不用 sms4j starter
 * （需支持纯 Java），因此在这里提供同一批内置 Factory 实例，由装载器经 sms4j 原生
 * 入口 {@code ProviderFactoryHolder.registerFactory(factory)} 注册——内置与自定义
 * 渠道走完全相同的注册路径。</p>
 *
 * <p>本类只提供「标识 → 官方 Factory 实例」，不含任何渠道实现；清单外的厂商由
 * 使用方按 sms4j 文档自行实现 Factory，无需改动本类。</p>
 *
 * @author lianghang
 */
public final class SmsSupplierFactories {

    private static final String PREFIX = "org.dromara.sms4j.";
    private static final String FACTORY_SUFFIX = ".config.";

    /** 内置渠道标识 → sms4j 官方 Factory 全类名。 */
    private static final Map<String, String> FACTORY_CLASS_BY_SUPPLIER;

    static {
        Map<String, String> m = new LinkedHashMap<String, String>();
        put(m, "alibaba", "aliyun", "AlibabaFactory");
        put(m, "baidu", "baidu", "BaiduFactory");
        put(m, "chuanglan", "chuanglan", "ChuangLanFactory");
        put(m, "cloopen", "cloopen", "CloopenFactory");
        put(m, "ctyun", "ctyun", "CtyunFactory");
        put(m, "danmi", "danmi", "DanMiFactory");
        put(m, "dingzhong", "dingzhong", "DingZhongFactory");
        put(m, "emay", "emay", "EmayFactory");
        put(m, "huawei", "huawei", "HuaweiFactory");
        put(m, "jdcloud", "jdcloud", "JdCloudFactory");
        put(m, "jg", "jg", "JgFactory");
        put(m, "lianlu", "lianlu", "LianLuFactory");
        put(m, "luosimao", "luosimao", "LuoSiMaoFactory");
        put(m, "mas", "mas", "MasFactory");
        put(m, "netease", "netease", "NeteaseFactory");
        put(m, "qiniu", "qiniu", "QiNiuFactory");
        put(m, "submail", "submail", "SubMailFactory");
        put(m, "tencent", "tencent", "TencentFactory");
        put(m, "unisms", "unisms", "UniFactory");
        put(m, "yixintong", "yixintong", "YiXintongFactory");
        put(m, "yunpian", "yunpian", "YunPianFactory");
        put(m, "zhutong", "zhutong", "ZhutongFactory");
        FACTORY_CLASS_BY_SUPPLIER = Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, String> m, String supplier, String pkg, String factory) {
        m.put(supplier, PREFIX + pkg + FACTORY_SUFFIX + factory);
    }

    /**
     * 取内置渠道的官方 Factory 实例：反射调用其 {@code instance()}（与官方 starter 一致）；
     * 未知标识返回 null。
     */
    @SuppressWarnings("unchecked")
    public static BaseProviderFactory<?, ?> factoryInstance(String supplier) {
        String className = supplier == null ? null : FACTORY_CLASS_BY_SUPPLIER.get(supplier);
        if (className == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Method instance = clazz.getMethod("instance");
            return (BaseProviderFactory<?, ?>) instance.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("无法装载 sms4j 内置 Factory: " + className, e);
        }
    }

    /** 内置的渠道标识清单（不可变）。 */
    public static Map<String, String> suppliers() {
        return FACTORY_CLASS_BY_SUPPLIER;
    }

    private SmsSupplierFactories() {
    }
}
