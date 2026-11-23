package com.sfi.conflux.file.exception;

import com.sfi.conflux.common.exception.ErrorCode;

/**
 * 文件域错误码枚举（5xxx 段）。
 *
 * <p>错误码分段：1xxx 通用、2xxx 支付、3xxx 发票、4xxx 短信、5xxx 文件。</p>
 *
 * @author lianghang
 */
public enum FileErrorCode implements ErrorCode {

    CHANNEL_NOT_FOUND(5101, "存储平台未注册或未激活", 500),
    CAPABILITY_NOT_SUPPORTED(5102, "存储平台不支持该能力", 500),
    CONFIG_MISSING(5103, "存储平台配置缺失", 500),
    CONFIG_CONFLICT(5104, "存储平台配置冲突", 500),
    CHANNEL_CALL_FAILED(5105, "存储平台调用失败", 500),
    BAD_REQUEST(5106, "请求参数错误", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    FileErrorCode(int code, String message, int httpStatus) {
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
}
