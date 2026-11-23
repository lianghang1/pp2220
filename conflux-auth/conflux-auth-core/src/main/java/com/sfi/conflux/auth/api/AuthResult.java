package com.sfi.conflux.auth.api;

/**
 * 登录统一结果包装：所有渠道、所有形态共用。
 *
 * <p>成功用 {@link #ok(Object)}，失败用 {@link #fail(String, String)}。错误码为字符串
 * （登录域不沿用支付/发票的数字段码，各平台错误语义由 provider 在 errorCode 中表达）。</p>
 *
 * @author lianghang
 */
public class AuthResult<T> {

    private boolean success;
    private String errorCode;
    private String errorMsg;
    private T data;

    public static <T> AuthResult<T> ok(T data) {
        AuthResult<T> r = new AuthResult<T>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> AuthResult<T> fail(String errorCode, String errorMsg) {
        AuthResult<T> r = new AuthResult<T>();
        r.success = false;
        r.errorCode = errorCode;
        r.errorMsg = errorMsg;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public T getData() {
        return data;
    }
}
