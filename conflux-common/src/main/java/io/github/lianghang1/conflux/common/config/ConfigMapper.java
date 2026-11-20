package io.github.lianghang1.conflux.common.config;

import io.github.lianghang1.conflux.common.util.JsonUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一配置映射器：把「公共字段 + attr（自由键值）」映射进强类型配置对象。
 *
 * <p>四域共用的唯一映射入口。配置对象统一形态为「公共字段 + {@code Map<String,String> attr}」：
 * 公共字段由框架理解并显式设置；attr 的键即目标 config 的字段名（含从基类继承的字段），
 * 由本类按 Jackson 属性语义回填，框架对 attr 内容零感知。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>JDK8 兼容、复用框架共享 {@link JsonUtils} 单例 ObjectMapper，不引入新依赖；</li>
 *   <li>{@link #fill(Object, Map)} 采用「更新已有实例」语义：只覆盖 attr 中出现的字段，
 *       target 上已设置的公共字段与其它字段保持不变——因此调用的装载层应「先 fill attr、
 *       再显式 set 公共字段」，实现公共字段权威；</li>
 *   <li>支持 String→int/long/boolean/枚举等基础类型转换（由 ObjectMapper 统一处理）；</li>
 *   <li>类型不匹配、非法值等异常统一抛 {@link IllegalArgumentException}，由调用方按配置错误处理。</li>
 * </ul>
 *
 * @author lianghang
 */
public final class ConfigMapper {

    private ConfigMapper() {
    }

    /**
     * 把 attr 映射进已构造的目标配置对象（更新已有实例，含继承字段）。
     *
     * <p>只覆盖 attr 中显式出现的字段；target 上已设置的字段保持不变。attr 为 null/空时
     * 原样返回 target。</p>
     *
     * @param target 已构造（可已设置公共字段）的配置对象，非 null
     * @param attr   自由键值；键名按 Jackson 属性语义匹配 target 字段（忽略未知键）
     * @return 同一个 target 实例（便于链式使用）
     */
    public static <T> T fill(T target, Map<String, String> attr) {
        if (target == null) {
            throw new IllegalArgumentException("ConfigMapper.fill target 不能为空");
        }
        if (attr == null || attr.isEmpty()) {
            return target;
        }
        try {
            // readerForUpdating：以 target 为更新目标，只覆盖 attr 中出现的属性；
            // ObjectMapper 已配置 FAIL_ON_UNKNOWN_PROPERTIES=false，attr 多余键被忽略。
            JsonUtils.getObjectMapper().readerForUpdating(target).readValue(
                    JsonUtils.getObjectMapper().writeValueAsString(attr));
            return target;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "配置映射失败: target=" + target.getClass().getName() + ", " + e.getMessage(), e);
        }
    }

    /**
     * 由一张完整 map 构造强类型配置对象（适用于 map 即全部字段的场景）。
     *
     * @param map  完整字段键值对
     * @param type 目标 config 类型
     */
    public static <T> T fromMap(Map<String, String> map, Class<T> type) {
        if (map == null) {
            map = Collections.emptyMap();
        }
        try {
            return JsonUtils.getObjectMapper().convertValue(map, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "配置映射失败: target=" + type.getName() + ", " + e.getMessage(), e);
        }
    }

    /**
     * 归一化别名键：把别名映射到标准键（标准键已存在时不覆盖）。
     *
     * <p>用于收敛散落各渠道的字段名兼容（如 salerTaxNum→sellerTaxNo），
     * 统一在装载层调用，渠道实现不再各自猜测。</p>
     */
    public static Map<String, String> normalize(Map<String, String> attr, Map<String, String> aliases) {
        if (attr == null || aliases == null || aliases.isEmpty()) {
            return attr;
        }
        Map<String, String> result = new LinkedHashMap<String, String>(attr);
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String from = alias.getKey();
            String to = alias.getValue();
            if (result.containsKey(from) && !result.containsKey(to)) {
                result.put(to, result.get(from));
            }
        }
        return result;
    }
}
