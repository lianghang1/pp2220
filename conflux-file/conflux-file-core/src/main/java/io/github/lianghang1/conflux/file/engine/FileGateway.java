package io.github.lianghang1.conflux.file.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.file.channel.FileChannelProvider;
import io.github.lianghang1.conflux.file.config.FileChannelConfig;
import io.github.lianghang1.conflux.file.config.FileConfigProvider;
import io.github.lianghang1.conflux.file.exception.FileErrorCode;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.copy.CopyPretreatment;
import org.dromara.x.file.storage.core.move.MovePretreatment;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合文件网关门面：统一文件 API 的唯一出口，双形态共享——
 * 纯 jar 经 {@link #builder()} 手动组装，Spring Boot 由 starter 装配为单例 Bean。
 *
 * <p>与支付/发票/短信门面对齐的对外契约：{@link Builder#configProvider(FileConfigProvider)}
 * 注入配置源（文件/DB/配置中心均可），{@link #registerChannel(FileChannelProvider)} 声明自定义
 * 平台，{@link #registeredChannels()} 查看已声明平台。每次调用都从 {@link FileConfigProvider}
 * 读取配置并确保装载，配置变更可被装载层感知并重建 {@link FileStorageService}。</p>
 *
 * <p>内部完全委托 x-file-storage 原始包：{@link FileStorageRegistry} 负责配置装载，
 * 本门面按「configId + 平台」取出官方 {@link FileStorageService} 调用原生方法，
 * 返回官方原始结果对象（{@link FileInfo} / {@link Downloader} / Pretreatment 等），
 * 不另造结果对象、不重做平台能力。SDK 无状态：不托管 FileRecorder、不落库。</p>
 *
 * <p>门面方法严格限于最小可用集 + 已放开的 B/C：上传/下载/删除/存在性/预签名/复制/移动
 * + 原生出口。进阶能力（分片、ACL、元数据、列举、图片处理等）一律经 {@link #nativeService}
 * 用官方 API，门面不重复实现。</p>
 *
 * @author lianghang
 */
public class FileGateway {

    private final FileStorageRegistry registry;
    private final FileConfigProvider configProvider;

    private FileGateway(Builder builder) {
        this.registry = builder.registry;
        this.configProvider = builder.configProvider;
    }

    /** 创建构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 声明自定义存储平台（与三域 {@code registerChannel(provider)} 对齐）：登记一个
     * x-file-storage 原生 FileStorage 实例。平台实现仍全部来自 x-file-storage 或由使用方
     * 按其官方文档扩展，本方法不含任何平台逻辑。
     */
    public void registerChannel(FileChannelProvider provider) {
        registry.register(provider);
    }

    /** 已声明平台标识集合（对齐三域 registeredChannels）。 */
    public Set<String> registeredChannels() {
        return registry.registeredChannels();
    }

    // ---- 基线业务方法：全部委托 x-file-storage 官方 FileStorageService ----

    /** 上传：委托 service.of(source).setPlatform(channelCode).upload()。 */
    public FileInfo upload(String configId, String channelCode, Object source) {
        return service(configId, channelCode).of(source).setPlatform(channelCode).upload();
    }

    /** 上传（指定文件名）：支持 byte[]/InputStream/URL 等无法自动取文件名的来源。 */
    public FileInfo upload(String configId, String channelCode, Object source, String name) {
        return service(configId, channelCode).of(source, name).setPlatform(channelCode).upload();
    }

    /** 下载：委托 service.download(fileInfo)，返回官方 Downloader 供 bytes/file/outputStream。 */
    public Downloader download(String configId, String channelCode, FileInfo fileInfo) {
        return service(configId, channelCode).download(fileInfo);
    }

    /** 删除：委托 service.delete(fileInfo)。 */
    public boolean delete(String configId, String channelCode, FileInfo fileInfo) {
        return service(configId, channelCode).delete(fileInfo);
    }

    /** 存在性判断：委托 service.exists(fileInfo)。 */
    public boolean exists(String configId, String channelCode, FileInfo fileInfo) {
        return service(configId, channelCode).exists(fileInfo);
    }

    // ---- 放开的 B / C：同样纯委托官方原生方法 ----

    /** 生成预签名 URL（B）：委托 service.generatePresignedUrl(fileInfo, expiration)。 */
    public String generatePresignedUrl(String configId, String channelCode, FileInfo fileInfo, Date expiration) {
        return service(configId, channelCode).generatePresignedUrl(fileInfo, expiration);
    }

    /** 复制（C）：委托 service.copy(fileInfo)，返回官方 CopyPretreatment 供链式设置目标。 */
    public CopyPretreatment copy(String configId, String channelCode, FileInfo fileInfo) {
        return service(configId, channelCode).copy(fileInfo);
    }

    /** 移动（C）：委托 service.move(fileInfo)，返回官方 MovePretreatment 供链式设置目标。 */
    public MovePretreatment move(String configId, String channelCode, FileInfo fileInfo) {
        return service(configId, channelCode).move(fileInfo);
    }

    /** 原生出口：返回官方 FileStorageService，进阶能力（分片/ACL/元数据/列举等）直接用原生 API。 */
    public FileStorageService nativeService(String configId, String channelCode) {
        return service(configId, channelCode);
    }

    /** 按 configId + 平台取已装载 service：每次从配置源读取并确保装载。 */
    private FileStorageService service(String configId, String channelCode) {
        if (StringUtils.isBlank(configId) || StringUtils.isBlank(channelCode)) {
            throw new GatewayException(FileErrorCode.BAD_REQUEST, "configId/channelCode 必填");
        }
        FileChannelConfig cfg = configProvider.getConfig(configId, channelCode);
        if (cfg == null) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置缺失: configId={}, channelCode={}", configId, channelCode);
        }
        List<FileChannelConfig> all = allConfigsOf(configId, cfg);
        return registry.ensureLoaded(configId, all);
    }

    /** 汇总某 configId 的全部平台配置：优先用 provider.allConfigs() 过滤，兜底用当前单条。 */
    private List<FileChannelConfig> allConfigsOf(String configId, FileChannelConfig current) {
        List<FileChannelConfig> all = new ArrayList<FileChannelConfig>();
        for (FileChannelConfig cfg : configProvider.allConfigs()) {
            if (configId.equals(cfg.getConfigId())) {
                all.add(cfg);
            }
        }
        if (all.isEmpty()) {
            all.add(current);
        }
        return all;
    }

    /** 网关构建器：持有装载器与配置提供者，零外部框架依赖。 */
    public static class Builder {

        private final FileStorageRegistry registry = new FileStorageRegistry();
        private FileConfigProvider configProvider;

        /** 声明自定义平台（对齐三域同名方法），可多次调用。 */
        public Builder channel(FileChannelProvider provider) {
            registry.register(provider);
            return this;
        }

        /** 平台配置提供者（必填，对齐三域同名方法）：业务链路每次从中读取配置。 */
        public Builder configProvider(FileConfigProvider configProvider) {
            this.configProvider = configProvider;
            return this;
        }

        /** 构建；配置提供者必填（对齐三域），并按 configId 聚合全部配置预热装载、坏配置 fail-fast。 */
        public FileGateway build() {
            if (configProvider == null) {
                throw new GatewayException(FileErrorCode.CONFIG_MISSING, "未设置平台配置提供者");
            }
            Map<String, List<FileChannelConfig>> byApp = new LinkedHashMap<String, List<FileChannelConfig>>();
            for (FileChannelConfig cfg : configProvider.allConfigs()) {
                List<FileChannelConfig> list = byApp.get(cfg.getConfigId());
                if (list == null) {
                    list = new ArrayList<FileChannelConfig>();
                    byApp.put(cfg.getConfigId(), list);
                }
                list.add(cfg);
            }
            for (Map.Entry<String, List<FileChannelConfig>> entry : byApp.entrySet()) {
                registry.ensureLoaded(entry.getKey(), entry.getValue());
            }
            return new FileGateway(this);
        }
    }
}
