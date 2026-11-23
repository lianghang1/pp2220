package com.sfi.conflux.sms.exception;

import com.sfi.conflux.common.exception.ErrorCode;

/**
 * 短信域错误码枚举（4xxx 段）。
 *
 * <p>错误码分段：1xxx 通用、2xxx 支付、3xxx 发票、4xxx 短信。</p>
 *
 * @author lianghang
 */
public enum SmsErrorCode implements ErrorCode {

    CHANNEL_NOT_FOUND(4101, "渠道未注册或未激活", 500),
    CAPABILITY_NOT_SUPPORTED(4102, "渠道不支持该能力", 500),
    CONFIG_MISSING(4103, "渠道配置缺失", 500),
    CONFIG_CONFLICT(4104, "渠道配置冲突", 500),
    CHANNEL_CALL_FAILED(4105, "渠道调用失败", 500),
    BAD_REQUEST(4106, "请求参数错误", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    SmsErrorCode(int code, String message, int httpStatus) {
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
