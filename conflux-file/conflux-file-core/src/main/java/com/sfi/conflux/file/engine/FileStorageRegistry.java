package com.sfi.conflux.file.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.file.channel.FileChannelProvider;
import com.sfi.conflux.file.config.FileChannelConfig;
import com.sfi.conflux.file.exception.FileErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件平台装载器：Conflux 扁平配置 → x-file-storage 官方 FileStorageService。
 *
 * <p>只做「配置装载 + 路由」两件事，不含任何平台实现：</p>
 * <ul>
 *   <li>内置平台：对每个 channelCode（= 官方平台段名，如 aliyunOss/minio/local），反射取
 *       {@link FileStorageProperties} 的同名 {@code List<XxxConfig>} 段及泛型 Config 类，
 *       用 {@link JsonUtils#convertValue} 把扁平 attr 转成该官方 Config，注入
 *       {@code platform=channelCode}，加入段后整体交给官方 builder。不硬编码平台清单，
 *       官方新增平台段自动适配；只映射用户实际配置的段，不扫全部平台类（规避 optional SDK）。</li>
 *   <li>自定义平台：{@link FileChannelProvider} 承载的官方 {@code FileStorage} 实例经官方
 *       {@code addFileStorage(...)} 注入，与内置平台同一个 service 管理。</li>
 * </ul>
 *
 * <p>configId 维度隔离：每个 configId 独立 service；配置指纹变更时重建（凭证热更新）。
 * 注册在启动/配置变更期完成，运行期只读，线程安全。</p>
 *
 * <p><b>不托管 FileRecorder：</b>官方 {@code useDefault()} 自带空实现
 * {@code DefaultFileRecorder}，保证 upload/delete(FileInfo) 脱离 DB 可用。</p>
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
    /** 已声明渠道编码集合（与其余四域 registeredChannels 对齐）。 */
    private final Set<String> registeredChannels = ConcurrentHashMap.newKeySet();

    /** 声明自定义平台：记录官方 FileStorage 实例，构建时经 addFileStorage 注入。 */
    public void register(FileChannelProvider provider) {
        if (provider == null || provider.channelCode() == null || provider.channelCode().trim().isEmpty()) {
            throw new GatewayException(FileErrorCode.BAD_REQUEST, "渠道声明或 channelCode 为空");
        }
        customProviders.put(provider.channelCode(), provider);
        registeredChannels.add(provider.channelCode());
        fingerprints.clear();
        services.clear();
    }

    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(registeredChannels);
    }

    /**
     * 按配置对象确保装载：每次重新聚合该 configId 的全部配置并比对指纹，变更即重建 service。
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
            for (FileChannelConfig cfg : allConfigs) {
                registeredChannels.add(cfg.getChannelCode());
            }
            log.info("文件平台装载完成: configId={}, platforms={}", configId, platformNames(allConfigs));
            return service;
        }
    }

    /** 构建官方 FileStorageService：逐条反射映射扁平配置 → 官方 builder → 注入自定义平台。 */
    private FileStorageService build(String configId, List<FileChannelConfig> allConfigs) {
        FileStorageProperties merged = new FileStorageProperties();
        for (FileChannelConfig cfg : allConfigs) {
            applyConfig(merged, cfg);
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
     * 把一条扁平配置映射进官方 FileStorageProperties：
     * channelCode → 同名 List 段 → 泛型 Config 类 → attr 转 Config → setPlatform(channelCode)。
     * 不逐平台硬编码；只碰用户配置的段。
     */
    @SuppressWarnings("unchecked")
    private static void applyConfig(FileStorageProperties target, FileChannelConfig cfg) {
        String platformType = cfg.getChannelCode();
        Field field;
        try {
            field = FileStorageProperties.class.getDeclaredField(platformType);
        } catch (NoSuchFieldException e) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "未知文件平台类型: channelCode={}（须为 x-file-storage 平台段名，如 local/aliyunOss/minio）",
                    platformType, e);
        }
        if (!List.class.isAssignableFrom(field.getType())) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "channelCode 不是有效的文件平台段: {}", platformType);
        }
        Class<?> configClass = configClassOf(field, platformType);
        Object official;
        try {
            // attr 键名归一化：kebab-case(access-key) → camelCase(accessKey)，对齐官方 Config 字段
            official = JsonUtils.convertValue(normalizeKeys(cfg.getAttr()), configClass);
        } catch (Exception e) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "文件平台配置解析失败: channelCode={}, 请检查 attr 键名是否对齐官方 Config 字段",
                    platformType, e);
        }
        // 实例名 = channelCode（用户只写一次，官方实例与路由自动对齐）
        ((FileStorageProperties.BaseConfig) official).setPlatform(platformType);
        try {
            field.setAccessible(true);
            Object list = field.get(target);
            if (list instanceof List) {
                ((List<Object>) list).add(official);
            }
        } catch (IllegalAccessException e) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "文件平台配置装载失败: channelCode={}", platformType, e);
        }
    }

    /** kebab-case 键名（access-key）归一化为 camelCase（accessKey）；其余原样保留。 */
    private static java.util.Map<String, Object> normalizeKeys(java.util.Map<String, String> attr) {
        java.util.Map<String, Object> normalized = new java.util.LinkedHashMap<String, Object>();
        for (java.util.Map.Entry<String, String> e : attr.entrySet()) {
            String key = e.getKey();
            if (key != null && key.indexOf('-') >= 0) {
                StringBuilder sb = new StringBuilder();
                boolean upperNext = false;
                for (char c : key.toCharArray()) {
                    if (c == '-') {
                        upperNext = true;
                    } else {
                        sb.append(upperNext ? Character.toUpperCase(c) : c);
                        upperNext = false;
                    }
                }
                key = sb.toString();
            }
            normalized.put(key, e.getValue());
        }
        return normalized;
    }

    /** 从 {@code List<XxxConfig>} 字段的泛型签名取出官方 Config 类。 */
    private static Class<?> configClassOf(Field field, String platformType) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "无法解析文件平台配置类型: channelCode={}", platformType);
        }
        Type arg = ((ParameterizedType) generic).getActualTypeArguments()[0];
        if (arg instanceof WildcardType) {
            Type[] upper = ((WildcardType) arg).getUpperBounds();
            if (upper.length > 0) {
                arg = upper[0];
            }
        }
        if (!(arg instanceof Class)) {
            throw new GatewayException(FileErrorCode.CONFIG_CONFLICT,
                    "无法解析文件平台配置类: channelCode={}", platformType);
        }
        return (Class<?>) arg;
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

    /** 配置指纹：按 channelCode 排序后拼接其 attr JSON，变更即重建。 */
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
                    .append(JsonUtils.toJson(cfg.getAttr())).append('\u0001');
        }
        return sb.toString();
    }

    private static List<String> platformNames(List<FileChannelConfig> configs) {
        List<String> names = new ArrayList<String>();
        for (FileChannelConfig cfg : configs) {
            names.add(cfg.getChannelCode());
        }
        return names;
    }
}
