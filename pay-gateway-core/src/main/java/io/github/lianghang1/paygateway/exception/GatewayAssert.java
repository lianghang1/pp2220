package io.github.lianghang1.paygateway.exception;

import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 网关断言工具：校验失败直接抛 {@link GatewayException}。
 *
 * <p>守卫语句写在方法开头，校验与失败语义一行表达，
 * 替代散落的 if/throw 模板。</p>
 *
 * @author lianghang
 */
public final class GatewayAssert {

    private GatewayAssert() {
    }

    /** 条件不成立时抛出指定错误码的网关异常。 */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new GatewayException(errorCode);
        }
    }

    /** 条件不成立时抛出指定错误码 + 自定义文案的网关异常。 */
    public static void isTrue(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new GatewayException(errorCode, message);
        }
    }

    /** 条件不成立时抛出指定错误码 + {@code {}} 占位符文案的网关异常。 */
    public static void isTrue(boolean expression, ErrorCode errorCode, String message, Object... args) {
        if (!expression) {
            throw new GatewayException(errorCode, message, args);
        }
    }

    /** 对象为 null 时抛出网关异常。 */
    public static <T> T notNull(T object, ErrorCode errorCode) {
        if (object == null) {
            throw new GatewayException(errorCode);
        }
        return object;
    }

    /** 对象为 null 时抛出网关异常 + 自定义文案。 */
    public static <T> T notNull(T object, ErrorCode errorCode, String message) {
        if (object == null) {
            throw new GatewayException(errorCode, message);
        }
        return object;
    }

    /** 对象为 null 时抛出网关异常 + {@code {}} 占位符文案。 */
    public static <T> T notNull(T object, ErrorCode errorCode, String message, Object... args) {
        if (object == null) {
            throw new GatewayException(errorCode, message, args);
        }
        return object;
    }

    /** 字符串空白（null/空串/纯空白）时抛出网关异常 + 自定义文案。 */
    public static String notBlank(String text, ErrorCode errorCode, String message) {
        if (StringUtils.isBlank(text)) {
            throw new GatewayException(errorCode, message);
        }
        return text;
    }

    /** 字符串空白时抛出网关异常 + {@code {}} 占位符文案。 */
    public static String notBlank(String text, ErrorCode errorCode, String message, Object... args) {
        if (StringUtils.isBlank(text)) {
            throw new GatewayException(errorCode, message, args);
        }
        return text;
    }

    /** 无条件抛出网关异常（用于 switch 兜底、不可达分支等）。 */
    public static void fail(ErrorCode errorCode) {
        throw new GatewayException(errorCode);
    }

    /** 无条件抛出网关异常 + 自定义文案。 */
    public static void fail(ErrorCode errorCode, String message) {
        throw new GatewayException(errorCode, message);
    }

    /** 参数类断言的默认错误码快捷方法：条件不成立按参数错误抛出。 */
    public static void param(boolean expression, String message) {
        if (!expression) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST, message);
        }
    }

    /** 参数类断言快捷方法 + {@code {}} 占位符文案。 */
    public static void param(boolean expression, String message, Object... args) {
        if (!expression) {
            throw new GatewayException(GatewayErrorCode.BAD_REQUEST, message, args);
        }
    }
}
