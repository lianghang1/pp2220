package io.github.lianghang1.conflux.common.util;

import com.google.common.base.CaseFormat;

/**
 * 字符串工具统一入口：继承 commons-lang3 的
 * {@code org.apache.commons.lang3.StringUtils}（全量静态方法穿透可用），
 * 仅追加 lang3/guava 都没有的高频自有语义。
 *
 * @author lianghang
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    private StringUtils() {
    }

    /** 驼峰 → 下划线（userName → user_name），委托 guava CaseFormat */
    public static String camelToSnake(String text) {
        return text == null ? null
                : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    /** 下划线 → 驼峰（user_name → userName），委托 guava CaseFormat */
    public static String snakeToCamel(String text) {
        return text == null ? null
                : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, text);
    }

    /**
     * {@code {}} 占位符格式化（顺序替换）：{@code format("用户{}不存在: {}", "张三", 42L)}。
     * 占位符多于参数时多余占位符原样保留；参数多于占位符时多余参数忽略。
     */
    public static String format(String template, Object... args) {
        if (template == null || template.isEmpty() || args == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + args.length * 8);
        int argIndex = 0;
        int from = 0;
        int idx;
        while (argIndex < args.length && (idx = template.indexOf("{}", from)) >= 0) {
            sb.append(template, from, idx).append(args[argIndex++]);
            from = idx + 2;
        }
        sb.append(template, from, template.length());
        return sb.toString();
    }
}
