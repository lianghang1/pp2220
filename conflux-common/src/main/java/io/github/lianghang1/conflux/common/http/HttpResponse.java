package io.github.lianghang1.conflux.common.http;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原始 HTTP 响应值对象：只承载 statusCode、响应体与响应头，不含任何业务判定。
 *
 * <p>传输层（{@link HttpTransport}）成功执行请求后总是返回本对象，即使 HTTP 状态
 * 非 2xx 也不抛异常——「成功/失败」由渠道或业务层依据各自协议决定
 * （PayPal 看 2xx、云闪付看 respCode、微信 V2 看 return_code/result_code）。
 * {@link #isSuccessful()} 只提供 2xx 便捷判断，不参与传输控制。</p>
 *
 * <p>响应体同时提供两种视图：{@link #getBody()} 为按 UTF-8 解码的文本（渠道报文场景），
 * {@link #getRawBytes()} 为原始字节（版式文件下载等二进制场景）。二进制请求经
 * {@link HttpTransport#getBinary} 发起；两种视图共享同一份字节，互不损坏。</p>
 *
 * @author lianghang
 */
public final class HttpResponse {

    private final int statusCode;
    private final byte[] rawBytes;
    private final Map<String, String> headers;

    public HttpResponse(int statusCode, String body, Map<String, String> headers) {
        this(statusCode,
                body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8),
                headers);
    }

    public HttpResponse(int statusCode, byte[] rawBytes, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.rawBytes = rawBytes == null ? new byte[0] : rawBytes;
        this.headers = headers == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** 响应体文本（已按 UTF-8 读取，绝不为 null，空响应为 ""）。 */
    public String getBody() {
        return new String(rawBytes, StandardCharsets.UTF_8);
    }

    /** 响应体原始字节（二进制下载场景使用，绝不为 null）。 */
    public byte[] getRawBytes() {
        return rawBytes;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /** 忽略大小写取响应头；不存在返回 null。 */
    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** HTTP 状态码是否落在 2xx（仅便捷判断，不代表业务成功）。 */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
        return "HttpResponse{status=" + statusCode + ", bodyLength=" + rawBytes.length + "}";
    }
}
