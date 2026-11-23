package com.sfi.conflux.auth.exception;

import com.sfi.conflux.common.exception.ErrorCode;
import com.sfi.conflux.common.exception.GatewayException;

/**
 * 登录域错误码枚举（6xxx 段）。
 *
 * <p>错误码分段：1xxx 通用、2xxx 支付、3xxx 发票、4xxx 短信、5xxx 文件、6xxx 登录。</p>
 *
 * @author lianghang
 */
public enum AuthErrorCode implements ErrorCode {

    CHANNEL_NOT_FOUND(6101, "登录渠道未注册", 500),
    CONFIG_MISSING(6102, "登录渠道配置缺失", 500),
    BAD_REQUEST(6103, "请求参数错误", 400),
    CHANNEL_CALL_FAILED(6104, "登录渠道调用失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 快捷抛出为网关运行时异常（对齐四域 GatewayException.of 风格） */
    public GatewayException of(String message) {
        return new GatewayException(this, message);
    }
}
