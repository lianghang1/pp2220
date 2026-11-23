package com.sfi.conflux.common.exception;

/**
 * 网关错误码的统一契约。
 *
 * <p>错误码按能力域分段：1xxx 通用段（common.CommonErrorCode）、
 * 2xxx 支付段（pay.GatewayErrorCode）、3xxx 发票段（invoice.InvoiceErrorCode）。
 * common 只定义契约与通用段，不感知任何业务领域码。</p>
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
