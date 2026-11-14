package io.github.lianghang1.conflux.common.exception;

/**
 * 公共基座通用错误码（1xxx 段）：与任何业务领域无关的参数/资源/系统级语义。
 *
 * <p>错误码按能力域分段：1xxx 通用（本枚举）、2xxx 支付、3xxx 发票。
 * pay / invoice 各自的领域错误码实现 {@link ErrorCode}，通用工具与断言
 * （{@link GatewayAssert}、JSON、HTTP 传输层）只依赖本段，从而让 common
 * 保持零业务领域依赖。</p>
 *
 * @author lianghang
 */
public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST(1000, "请求参数错误", 400),
    NOT_FOUND(1404, "资源不存在", 404),
    CONFLICT(1409, "请求冲突或重复提交", 409),
    SYSTEM_ERROR(1500, "系统繁忙，请稍后再试", 500),
    HTTP_TRANSPORT_ERROR(1501, "HTTP 传输失败", 502),
    CONFIG_CONFLICT(1502, "配置键冲突，无法唯一确定账户", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(int code, String message, int httpStatus) {
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
