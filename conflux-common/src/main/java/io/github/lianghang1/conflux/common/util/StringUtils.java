package io.github.lianghang1.conflux.common.util;

/**
 * 字符串工具统一入口：继承 commons-lang3 的
 * {@code org.apache.commons.lang3.StringUtils}（全量静态方法穿透可用），
 * 仅追加 lang3 没有的高频自有语义。
 *
 * @author lianghang
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    private StringUtils() {
    }

    /** 驼峰 → 下划线（userName → user_name） */
    public static String camelToSnake(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 下划线 → 驼峰（user_name → userName） */
    public static String snakeToCamel(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean upperNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext && sb.length() > 0) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else if (sb.length() == 0) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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
