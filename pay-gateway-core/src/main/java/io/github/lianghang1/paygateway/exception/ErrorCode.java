package io.github.lianghang1.paygateway.exception;

/**
 * 网关错误码的统一契约。
 *
 * <p>错误码分段管理：1xxx 为通用段（参数/资源/系统），2xxx 为网关域（订单/渠道/持久化/通知）。
 * 渠道包内部如需细分错误语义，实现本接口自行分段，不写入 {@link GatewayErrorCode}。</p>
 *
 * @author lianghang
 */
public interface ErrorCode {

    /** 错误码。 */
    int getCode();

    /** 默认错误提示（面向调用方可读）。 */
    String getMessage();

    /**
     * 建议的 HTTP 状态码；返回 -1 表示由调用方自行决定。
     * 验签/参数类错误应给出 4xx，让渠道侧能感知失败并重试。
     */
    default int getHttpStatus() {
        return -1;
    }
}
