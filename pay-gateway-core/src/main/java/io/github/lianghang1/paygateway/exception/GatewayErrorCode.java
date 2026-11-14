package io.github.lianghang1.paygateway.exception;

/**
 * 网关通用错误码枚举。
 *
 * <p>只承载框架内跨模块的通用语义；渠道专属错误在渠道包内定义，
 * 渠道失败统一收敛为 {@link #CHANNEL_CALL_FAILED} 并携带渠道原始错误信息。</p>
 *
 * @author lianghang
 */
public enum GatewayErrorCode implements ErrorCode {

    // ---- 通用 1xxx ----
    BAD_REQUEST(1000, "请求参数错误", 400),
    NOT_FOUND(1404, "资源不存在", 404),
    CONFLICT(1409, "请求冲突或重复提交", 409),
    SYSTEM_ERROR(1500, "系统繁忙，请稍后再试", 500),

    // ---- 订单 2xxx ----
    ORDER_NOT_FOUND(2001, "订单不存在", 404),
    DUPLICATE_ORDER(2002, "相同业务单号的订单已存在且参数不一致", 409),
    STATE_ILLEGAL(2003, "订单当前状态不允许该操作", 409),
    AMOUNT_MISMATCH(2004, "回调金额与订单金额不一致", 409),
    REFUND_EXCEED_REFUNDABLE(2005, "退款金额超出可退余额", 409),
    PAYEE_REQUIRED(2006, "缺少收款方信息", 400),

    // ---- 渠道 21xx ----
    CHANNEL_NOT_FOUND(2101, "渠道未注册或未激活", 500),
    CHANNEL_CAPABILITY_NOT_SUPPORTED(2102, "渠道不支持该能力", 500),
    METHOD_NOT_SUPPORTED(2103, "渠道不支持该支付方式", 400),
    CONFIG_MISSING(2104, "渠道配置缺失", 500),
    CHANNEL_CALL_FAILED(2105, "渠道调用失败", 500),
    SIGN_INVALID(2106, "回调验签失败", 400),
    CALLBACK_PARSE_FAILED(2107, "回调报文解析失败", 400),

    // ---- 持久化/通知 22xx ----
    STORE_MISSING(2201, "持久化实现未注册", 500),
    NOTIFY_SEND_FAILED(2202, "下游通知发送失败", 500);

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
