package io.github.lianghang1.conflux.common.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原始 HTTP 响应值对象：只承载 statusCode、响应体字符串与响应头，不含任何业务判定。
 *
 * <p>传输层（{@link HttpTransport}）成功执行请求后总是返回本对象，即使 HTTP 状态
 * 非 2xx 也不抛异常——「成功/失败」由渠道或业务层依据各自协议决定
 * （PayPal 看 2xx、云闪付看 respCode、微信 V2 看 return_code/result_code）。
 * {@link #isSuccessful()} 只提供 2xx 便捷判断，不参与传输控制。</p>
 *
 * @author lianghang
 */
public final class HttpResponse {

    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;

    public HttpResponse(int statusCode, String body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.headers = headers == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** 响应体（已按 UTF-8 读取为字符串，绝不为 null，空响应为 ""）。 */
    public String getBody() {
        return body;
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
        return "HttpResponse{status=" + statusCode + ", bodyLength=" + body.length() + "}";
    }
}
