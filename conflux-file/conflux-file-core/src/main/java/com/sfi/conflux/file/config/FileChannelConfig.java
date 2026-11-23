package com.sfi.conflux.file.config;

import org.dromara.x.file.storage.core.FileStorageProperties;

/**
 * 文件渠道配置值对象：承载「configId + 渠道编码 + x-file-storage 官方平台配置」。
 *
 * <p>与支付域 {@code ChannelConfig}、发票域 {@code ChannelConfig}、通知域
 * {@code NotificationConfig} 同层次，由 {@link FileConfigProvider} 产出，交给装载器
 * 构建官方 {@code FileStorageService}。</p>
 *
 * <p><b>为什么不照搬 {@code attr: Map&lt;String,String&gt;}：</b> x-file-storage 没有像
 * sms4j {@code getConfigClass()} 那样的 Config 类自描述入口——它的平台配置按类型分组
 * （{@code FileStorageProperties} 内含 local/aliyunOss/minio 等 20 个 {@code List<XxxConfig>}
 * 段）。若用扁平 attr 反推具体 Config 类，就必须由我们维护一份「平台类型 → Config 类」
 * 清单并逐字段映射，这既重做了官方 builder 的构建逻辑，又会随官方升级而漂移。
 * 因此本配置直接承载官方 {@link FileStorageProperties}，平台 Config 的字段解析、
 * 客户端构建、平台实例创建<b>全部交给官方 {@code FileStorageServiceBuilder}</b>，
 * 我们只做装载编排——这是「不重做对方已有能力」约束下的最薄形态。</p>
 *
 * @author lianghang
 */
public final class FileChannelConfig {

    private final String configId;
    private final String channelCode;
    private final FileStorageProperties properties;

    public FileChannelConfig(String configId, String channelCode, FileStorageProperties properties) {
        this.configId = configId;
        this.channelCode = channelCode;
        if (properties == null) {
            throw new IllegalArgumentException("FileStorageProperties 不能为空");
        }
        this.properties = properties;
    }

    public String getConfigId() {
        return configId;
    }

    /** 渠道编码（等价于 x-file-storage 的 platform 实例名；对外统一叫 channelCode）。 */
    public String getChannelCode() {
        return channelCode;
    }

    public FileStorageProperties getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "FileChannelConfig{configId='" + configId + "', channelCode='" + channelCode + "'}";
    }
}
