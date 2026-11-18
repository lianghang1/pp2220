package io.github.lianghang1.conflux.invoice.support;

/**
 * 内存配置提供者的组合索引键工具。
 *
 * @author lianghang
 */
public final class ConfigKeys {
    private ConfigKeys() {
    }

    /** 以 {@code ::} 拼接非空安全的组合索引键。 */
    public static String key(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append("::");
            }
            sb.append(parts[i] == null ? "" : parts[i]);
        }
        return sb.toString();
    }
}
