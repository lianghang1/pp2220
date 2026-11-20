package io.github.lianghang1.conflux.pay.exception;

import io.github.lianghang1.conflux.common.exception.ErrorCode;

/**
 * 支付域错误码枚举（2xxx 段）。
 *
 * <p>只承载框架内跨模块的通用语义；渠道专属错误在渠道包内定义，
 * 渠道失败统一收敛为 {@link #CHANNEL_CALL_FAILED} 并携带渠道原始错误信息。</p>
 *
 * @author lianghang
 */
public enum GatewayErrorCode implements ErrorCode {

    // 1xxx 通用段已下沉至 common.CommonErrorCode；本枚举只承载 2xxx 支付段

    // ---- 订单 2xxx ----
    STATE_ILLEGAL(2003, "订单当前状态不允许该操作", 409),

    // ---- 渠道 21xx ----
    CHANNEL_NOT_FOUND(2101, "渠道未注册或未激活", 500),
    CHANNEL_CAPABILITY_NOT_SUPPORTED(2102, "渠道不支持该能力", 500),
    METHOD_NOT_SUPPORTED(2103, "渠道不支持该支付方式", 400),
    CONFIG_MISSING(2104, "渠道配置缺失", 500),
    CHANNEL_CALL_FAILED(2105, "渠道调用失败", 500),
    SIGN_INVALID(2106, "回调验签失败", 400),
    CALLBACK_PARSE_FAILED(2107, "回调报文解析失败", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    GatewayErrorCode(int code, String message, int httpStatus) {
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
