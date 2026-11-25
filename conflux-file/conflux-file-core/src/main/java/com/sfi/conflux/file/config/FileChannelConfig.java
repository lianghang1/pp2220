package com.sfi.conflux.file.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件渠道配置值对象：承载「configId + 渠道编码 + 扁平凭证」。
 *
 * <p>与支付域 {@code ChannelConfig}、发票域 {@code ChannelConfig}、通知域
 * {@code NotificationConfig}、登录域 {@code AuthConfig} 完全同构：公共维度（configId /
 * channelCode）为一等字段，平台个性凭证放 {@code attr: Map}，不含任何第三方官方类型。</p>
 *
 * <p><b>channelCode 语义</b>：即 x-file-storage 的<b>平台类型段名</b>（{@code local} /
 * {@code aliyun-oss} / {@code minio} …，官方 yml 的 kebab-case 与 Java 字段名 camelCase
 * 均支持），与支付域的 {@code wechat} / {@code alipay} 同为"类型编码"。装载器据此归一化后
 * 反射 {@code FileStorageProperties} 的同名 List 段、取官方 Config 类、把 attr 转成该 Config；
 * 平台实例名取传入的 channelCode 原值——不硬编码平台清单，官方新增平台自动适配。
 * 同一类型多实例按 configId 区分（与支付域服务商模式多个 configId 一致）。</p>
 *
 * @author lianghang
 */
public final class FileChannelConfig {

    private final String configId;
    private final String channelCode;
    private final Map<String, String> attr;

    public FileChannelConfig(String configId, String channelCode, Map<String, String> attr) {
        this.configId = configId;
        this.channelCode = channelCode;
        this.attr = attr == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(attr));
    }

    public String getConfigId() {
        return configId;
    }

    /** 渠道编码 = x-file-storage 平台类型段名（local/aliyunOss/minio...）。 */
    public String getChannelCode() {
        return channelCode;
    }

    /** 平台个性凭证（键名对齐官方 Config 字段，如 accessKey/basePath）。 */
    public Map<String, String> getAttr() {
        return attr;
    }

    @Override
    public String toString() {
        return "FileChannelConfig{configId='" + configId + "', channelCode='" + channelCode + "'}";
    }
}
