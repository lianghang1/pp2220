package io.github.lianghang1.conflux.sms.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信渠道标识 → sms4j 厂商 Factory 全类名的映射（装载元数据）。
 *
 * <p>本类只描述「渠道标识对应的 sms4j 工厂类」，不含任何渠道实现逻辑——
 * 真正的配置对象与发送能力全部来自 sms4j 原始包。清单与 sms4j 3.3.5
 * 官方 starter 的 {@code SupplierConfig#factoryList()} 一致；如 sms4j 新增
 * 厂商，使用方也可通过 {@link SmsBlendRegistry#registerFactory(String, String)}
 * 自行补充映射，无需改动本类。</p>
 *
 * @author lianghang
 */
public final class SmsSupplierFactories {

    private static final String PREFIX = "org.dromara.sms4j.";

    private static final Map<String, String> FACTORY_BY_SUPPLIER;

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
        FACTORY_BY_SUPPLIER = Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, String> m, String supplier, String pkg, String factory) {
        m.put(supplier, PREFIX + pkg + ".config." + factory);
    }

    /** 按渠道标识取 Factory 全类名；未知返回 null。同时兼容阿里云别名 aliyun。 */
    public static String factoryClassName(String supplier) {
        if (supplier == null) {
            return null;
        }
        String name = FACTORY_BY_SUPPLIER.get(supplier);
        if (name == null && "aliyun".equals(supplier)) {
            name = FACTORY_BY_SUPPLIER.get("alibaba");
        }
        return name;
    }

    /** 内置的渠道标识清单（不可变）。 */
    public static Map<String, String> suppliers() {
        return FACTORY_BY_SUPPLIER;
    }

    private SmsSupplierFactories() {
    }
}
