package com.sfi.conflux.common.exception;

import com.sfi.conflux.common.util.StringUtils;

/**
 * 网关运行时异常，框架内表达「可预期失败」的唯一方式。
 *
 * <p>渠道网络抖动、SDK 抛出的不可预期异常先包装为
 * 统一异常语义再上抛，保留原始 cause；
 * 使用方捕获本异常后可依据 {@link #getErrorCode()} 决定重试或终止。</p>
 *
 * @author lianghang
 */
public class GatewayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public GatewayException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义 message 覆盖错误码默认文案。 */
    public GatewayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GatewayException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** {@code {}} 占位符文案：{@code new GatewayException(code, "渠道{}不存在", channelCode)}。 */
    public GatewayException(ErrorCode errorCode, String message, Object... args) {
        super(message == null ? null : StringUtils.format(message, args));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 快捷抛出。 */
    public static GatewayException of(ErrorCode errorCode) {
        return new GatewayException(errorCode);
    }

    /** 快捷抛出（自定义文案）。 */
    public static GatewayException of(ErrorCode errorCode, String message) {
        return new GatewayException(errorCode, message);
    }

    /** 快捷抛出（{@code {}} 占位符文案）。 */
    public static GatewayException of(ErrorCode errorCode, String message, Object... args) {
        return new GatewayException(errorCode, message, args);
    }
}
