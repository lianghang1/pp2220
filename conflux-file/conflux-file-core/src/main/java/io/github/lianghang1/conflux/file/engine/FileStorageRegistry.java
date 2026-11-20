package io.github.lianghang1.conflux.file.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.file.channel.FileChannelProvider;
import io.github.lianghang1.conflux.file.config.FileChannelConfig;
import io.github.lianghang1.conflux.file.exception.FileErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件平台装载器：Conflux 配置 → x-file-storage 官方 FileStorageService。
 *
 * <p>本类只做「配置装载 + 路由」两件事，不含任何平台实现：</p>
 * <ul>
 *   <li>内置平台：按 configId 聚合各 platform 的官方 {@link FileStorageProperties}（合并各平台
 *       List 段），整体交给官方 {@link FileStorageServiceBuilder#create(FileStorageProperties)}
 *       → {@code useDefault()} → {@code build()}，由官方完成全部 20+ 平台的构建。</li>
 *   <li>自定义平台：{@link FileChannelProvider} 承载的官方 {@code FileStorage} 实例经官方
 *       {@code addFileStorage(...)} 注入，与内置平台由同一个 service 统一管理。</li>
 * </ul>
 *
 * <p>configId 维度隔离：每个 configId 持有独立 FileStorageService（避免跨 app 串配置）；配置指纹
 * 变更时重建该 configId 的 service（凭证热更新）。注册发生在启动/配置变更期，运行期只读，
 * 线程安全。</p>
 *
 * <p><b>不托管 FileRecorder：</b>官方 {@code useDefault()} 自带空实现
 * {@code DefaultFileRecorder}（save/delete 返回 true、getByUrl 抛明确异常），保证
 * upload/delete(FileInfo) 脱离 DB 可用；url→元信息反查能力不进门面。</p>
 *
 * @author lianghang
 */
@Slf4j
public class FileStorageRegistry {

    /** configId → 已构建的官方 FileStorageService。 */
    private final Map<String, FileStorageService> services = new ConcurrentHashMap<String, FileStorageService>();
    /** configId → 配置指纹；变更则重建该 configId 的 service。 */
    private final Map<String, String> fingerprints = new ConcurrentHashMap<String, String>();
    /** 自定义平台声明（channelCode → Provider），对所有 configId 生效。 */
    private final Map<String, FileChannelProvider> customProviders =
            new ConcurrentHashMap<String, FileChannelProvider>();
    /** 已声明渠道编码集合（与支付/发票/短信 registeredChannels 对齐）。 */
    private final Set<String> registeredChannels = ConcurrentHashMap.newKeySet();

    /**
     * 声明自定义平台（与三域 {@code registerChannel(provider)} 对齐）：把官方 FileStorage
     * 实例记录下来，构建 service 时经 addFileStorage 注入。
     */
    public void register(FileChannelProvider provider) {
        if (provider == null || provider.channelCode() == null || provider.channelCode().trim().isEmpty()) {
            throw new GatewayException(FileErrorCode.BAD_REQUEST, "渠道声明或 channelCode 为空");
        }
        customProviders.put(provider.channelCode(), provider);
        registeredChannels.add(provider.channelCode());
        // 已有 service 需重建以纳入新平台
        fingerprints.clear();
        services.clear();
    }

    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(registeredChannels);
    }

    /**
     * 按配置对象确保装载（业务链路：从 FileConfigProvider 取到配置后调用）。
     * 每次重新聚合该 configId 的全部配置并比对指纹，变更即重建 service。
     *
     * @param allConfigs 当前 configId 的全部平台配置
     * @return 已装载的官方 FileStorageService
     */
    public FileStorageService ensureLoaded(String configId, List<FileChannelConfig> allConfigs) {
        if (allConfigs == null || allConfigs.isEmpty()) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台配置为空: configId={}", configId);
        }
        String fingerprint = fingerprint(allConfigs);
        FileStorageService service = services.get(configId);
        if (service != null && fingerprint.equals(fingerprints.get(configId))) {
            return service;
        }
        synchronized (this) {
            service = services.get(configId);
            if (service != null && fingerprint.equals(fingerprints.get(configId))) {
                return service;
            }
            service = build(configId, allConfigs);
            services.put(configId, service);
            fingerprints.put(configId, fingerprint);
            // 声明的自定义平台编码也计入已注册集合
            for (FileChannelConfig cfg : allConfigs) {
                registeredChannels.add(cfg.getChannelCode());
            }
            log.info("文件平台装载完成: configId={}, platforms={}", configId, platformNames(allConfigs));
            return service;
        }
    }

    /** 构建官方 FileStorageService：合并各平台 properties → 官方 builder → 注入自定义平台。 */
    private FileStorageService build(String configId, List<FileChannelConfig> allConfigs) {
        FileStorageProperties merged = new FileStorageProperties();
        for (FileChannelConfig cfg : allConfigs) {
            mergeProperties(merged, cfg.getProperties());
        }
        try {
            FileStorageServiceBuilder builder = FileStorageServiceBuilder.create(merged).useDefault();
            for (FileChannelProvider provider : customProviders.values()) {
                builder.addFileStorage(provider.fileStorage());
            }
            return builder.build();
        } catch (Exception e) {
            throw new GatewayException(FileErrorCode.CHANNEL_CALL_FAILED,
                    "构建文件存储服务失败: configId=" + configId, e);
        }
    }

    /**
     * 通用合并两个 FileStorageProperties：反射遍历所有 {@code List} 字段逐段累加，
     * 不逐平台硬编码——这样官方新增平台时自动适配，我们不维护平台清单。
     */
    private static void mergeProperties(FileStorageProperties target, FileStorageProperties source) {
        for (Field field : FileStorageProperties.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object srcList = field.get(source);
                if (srcList == null) {
                    continue;
                }
                Object tgtList = field.get(target);
                if (tgtList instanceof List) {
                    ((List<Object>) tgtList).addAll((List<?>) srcList);
                }
            } catch (IllegalAccessException e) {
                throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                        "合并文件平台配置失败: " + field.getName(), e);
            }
        }
    }

    /** 取某 configId 已装载的 service；未装载抛明确异常。 */
    public FileStorageService service(String configId) {
        FileStorageService service = services.get(configId);
        if (service == null) {
            throw new GatewayException(FileErrorCode.CONFIG_MISSING,
                    "文件平台未装载: configId={}，请先调用 ensureLoaded", configId);
        }
        return service;
    }

    /** 配置指纹：按 configId+platform 排序后拼接 properties 的 JSON 序列，变更即重建。 */
    private static String fingerprint(List<FileChannelConfig> allConfigs) {
        List<FileChannelConfig> sorted = new ArrayList<FileChannelConfig>(allConfigs);
        Collections.sort(sorted, (a, b) -> {
            String pa = a == null ? "" : String.valueOf(a.getChannelCode());
            String pb = b == null ? "" : String.valueOf(b.getChannelCode());
            return pa.compareTo(pb);
        });
        StringBuilder sb = new StringBuilder();
        for (FileChannelConfig cfg : sorted) {
            if (cfg == null) {
                continue;
            }
            sb.append(cfg.getChannelCode()).append('=')
                    .append(toJson(cfg.getProperties())).append('\u0001');
        }
        return sb.toString();
    }

    /** 用 conflux-common 传递的 jackson 做 JSON 序列化，避免引入额外依赖。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            // 序列化异常不应阻断装载：退化为对象摘要，至多导致该配置不触发重建
            return obj.getClass().getName() + "@" + System.identityHashCode(obj);
        }
    }

    private static List<String> platformNames(List<FileChannelConfig> configs) {
        List<String> names = new ArrayList<String>();
        for (FileChannelConfig cfg : configs) {
            names.add(cfg.getChannelCode());
        }
        return names;
    }
}
