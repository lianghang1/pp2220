package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

/**
 * 百旺金穗云接口版本。
 *
 * <p>同一能力同时存在 V1 / V2 时默认走 {@link #V2}；V1 保留完整实现（含报文差异），
 * 由 {@code attr.apiVersion=V1} 切换，便于存量接入方平滑迁移。</p>
 *
 * @author lianghang
 */
public enum BaiwangJsApiVersion {

    /** 3.2.6 / 3.2.7 老版接口。 */
    V1("V1"),
    /** 3.2.6(V2) / 3.2.7(V2) 新版接口（默认）。 */
    V2("V2");

    private final String value;

    BaiwangJsApiVersion(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 解析配置值；空值/未知值一律回落 V2（不因拼写错误静默切到老接口）。 */
    public static BaiwangJsApiVersion parse(String text) {
        if (text == null) {
            return V2;
        }
        String trimmed = text.trim();
        for (BaiwangJsApiVersion version : values()) {
            if (version.value.equalsIgnoreCase(trimmed)
                    || version.name().equalsIgnoreCase(trimmed)) {
                return version;
            }
        }
        return V2;
    }
}
