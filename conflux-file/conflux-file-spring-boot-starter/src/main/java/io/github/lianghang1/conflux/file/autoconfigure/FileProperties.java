package io.github.lianghang1.conflux.file.autoconfigure;

import org.dromara.x.file.storage.core.FileStorageProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件网关 starter 配置（{@code conflux.file.*}）。
 *
 * <p>与支付/发票/短信 starter 同构的「应用 → 平台实例 → 配置」结构。第一层 key 为
 * configId，第二层 key 为平台实例标识（{@code platform}，如 aliyun-oss-1）；value 直接是
 * x-file-storage 官方 {@link FileStorageProperties}，其嵌套的各平台 List 段
 * （{@code aliyunOss} / {@code minio} / {@code local} …）由 Spring Boot 原生绑定，
 * 我们不做任何「平台类型 → Config 类」映射，完全复用官方配置结构。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * conflux:
 *   file:
 *     configs:
 *       app1:
 *         aliyun-oss-1:
 *           aliyunOss:
 *             - platform: aliyun-oss-1
 *               access-key: xx
 *               secret-key: xx
 *               end-point: xx
 *               bucket-name: xx
 *               domain: https://xx/
 * }</pre>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "conflux.file")
public class FileProperties {

    /** 应用 → 平台实例 → 官方平台配置。 */
    private Map<String, Map<String, FileStorageProperties>> configs =
            new LinkedHashMap<String, Map<String, FileStorageProperties>>();

    public Map<String, Map<String, FileStorageProperties>> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, Map<String, FileStorageProperties>> configs) {
        this.configs = configs;
    }
}
