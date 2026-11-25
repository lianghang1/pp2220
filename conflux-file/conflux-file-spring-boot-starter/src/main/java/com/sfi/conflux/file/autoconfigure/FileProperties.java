package com.sfi.conflux.file.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件网关 starter 配置（{@code conflux.file.*}）。
 *
 * <p>与支付/发票/通知/登录 starter 完全同构的「configId → channelCode → 配置」结构：
 * 第一层 key 为 configId，第二层 key 为 channelCode（= x-file-storage 平台类型段名，
 * 如 {@code local}/{@code aliyunOss}/{@code minio}），value 是自有扁平
 * {@link ChannelProperties}——只有 {@code attr} 一个键值包，不含任何第三方官方类型。
 * 平台个性字段（accessKey/basePath 等）放 attr，键名对齐官方 Config 字段；
 * 装载层反射映射成官方 Config，不硬编码平台清单。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * conflux:
 *   file:
 *     configs:
 *       app1:
 *         aliyun-oss:            # 官方 yml 平台段名（kebab-case），也兼容 aliyunOss
 *           attr:
 *             access-key: xx
 *             secret-key: xx
 *             end-point: xx
 *             bucket-name: xx
 *             domain: https://xx/
 * }</pre>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "conflux.file")
public class FileProperties {

    /** 应用 → 渠道编码(平台类型) → 渠道配置。 */
    private Map<String, Map<String, ChannelProperties>> configs =
            new LinkedHashMap<String, Map<String, ChannelProperties>>();

    public Map<String, Map<String, ChannelProperties>> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, Map<String, ChannelProperties>> configs) {
        this.configs = configs;
    }

    /** 单渠道配置：扁平键值包，与其余四域的 ChannelProperties 同构。 */
    public static class ChannelProperties {

        /**
         * 平台个性键值包（键名对齐官方 Config 字段，支持 kebab-case 与 camelCase）。
         * 由装载层经 JsonUtils.convertValue 转成对应的官方 XxxConfig。
         */
        private Map<String, String> attr = new LinkedHashMap<String, String>();

        public Map<String, String> getAttr() {
            return attr;
        }

        public void setAttr(Map<String, String> attr) {
            this.attr = attr;
        }
    }
}
