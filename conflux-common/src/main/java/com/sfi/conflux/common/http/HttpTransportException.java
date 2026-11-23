package com.sfi.conflux.common.http;

/**
 * HTTP 传输层中立的 IO 异常：把 OkHttp 的 {@link java.io.IOException}、
 * 非法 URL、报文构建失败等统一包装为运行时异常并保留 cause。
 *
 * <p>本异常<b>不携带任何业务错误码</b>，与 {@code GatewayException} 领域异常体系
 * 解耦——由调用方（渠道层）按自身语义翻译为具体错误码并决定是否重试。
 * 这样可以避免 common 依赖任何能力域。</p>
 *
 * @author lianghang
 */
public class HttpTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HttpTransportException(String message) {
        super(message);
    }

    public HttpTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
