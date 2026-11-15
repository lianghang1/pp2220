package io.github.lianghang1.conflux.common.http;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 公共 HTTP 传输层：OkHttp 单例复用 + 可配超时 + 只返回原始 {@link HttpResponse}。
 *
 * <p><b>边界</b>：本类只负责「组装 Request → 执行 → 读 body/headers → 返回原始响应」，
 * 不做任何业务判定、不解析 JSON/XML、不抛领域错误码。非 2xx 也正常返回
 * （成功与否由渠道层按各自协议决定），IO/构建异常统一中立化为
 * {@link HttpTransportException} 并保留 cause，由调用方翻译为自己的错误码。</p>
 *
 * <p><b>复用</b>：所有派生 client 由共享 {@code BASE} 经 {@code newBuilder()} 生成，
 * 共享连接池与调度线程池，仅覆盖超时；按 (connect/read/write) 三元组缓存，
 * 避免每个渠道各自 {@code new OkHttpClient()} 造成连接池碎片。</p>
 *
 * @author lianghang
 */
public final class HttpTransport {

    public static final String JSON = "application/json; charset=utf-8";
    public static final String XML = "text/xml; charset=utf-8";
    public static final String FORM = "application/x-www-form-urlencoded";

    private static final long DEFAULT_CONNECT_MILLIS = 10000L;
    private static final long DEFAULT_READ_MILLIS = 30000L;
    private static final long DEFAULT_WRITE_MILLIS = 10000L;

    private static final OkHttpClient BASE = new OkHttpClient.Builder().build();
    private static final ConcurrentHashMap<String, OkHttpClient> CLIENTS =
            new ConcurrentHashMap<String, OkHttpClient>();

    private HttpTransport() {
    }

    private static OkHttpClient client(long connect, long read, long write) {
        String key = connect + "/" + read + "/" + write;
        OkHttpClient cached = CLIENTS.get(key);
        if (cached != null) {
            return cached;
        }
        OkHttpClient derived = BASE.newBuilder()
                .connectTimeout(connect, TimeUnit.MILLISECONDS)
                .readTimeout(read, TimeUnit.MILLISECONDS)
                .writeTimeout(write, TimeUnit.MILLISECONDS)
                .build();
        OkHttpClient prev = CLIENTS.putIfAbsent(key, derived);
        return prev != null ? prev : derived;
    }

    /**
     * 通用执行入口。
     *
     * @param method        GET / POST
     * @param url           完整 URL
     * @param headers       请求头（可为 null）
     * @param mediaType     POST 的 Content-Type（GET 忽略）；null 取 {@link #JSON}
     * @param body          POST 报文字节（GET 忽略）；null 视为空报文
     * @param connectMillis 连接超时
     * @param readMillis    读取超时
     * @param writeMillis   写入超时
     * @return 原始响应（含非 2xx），绝不为 null
     */
    public static HttpResponse execute(String method, String url,
                                       Map<String, String> headers, String mediaType, byte[] body,
                                       long connectMillis, long readMillis, long writeMillis) {
        try {
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
            }
            if ("GET".equalsIgnoreCase(method)) {
                builder.get();
            } else if ("POST".equalsIgnoreCase(method)) {
                byte[] payload = body == null ? new byte[0] : body;
                MediaType type = MediaType.parse(mediaType == null ? JSON : mediaType);
                builder.post(RequestBody.create(payload, type));
            } else {
                throw new HttpTransportException("不支持的 HTTP 方法: " + method);
            }

            try (Response response = client(connectMillis, readMillis, writeMillis)
                    .newCall(builder.build()).execute()) {
                ResponseBody rb = response.body();
                byte[] bytes = rb == null ? new byte[0] : rb.bytes();
                Map<String, String> respHeaders = new LinkedHashMap<String, String>();
                Headers h = response.headers();
                for (int i = 0; i < h.size(); i++) {
                    respHeaders.put(h.name(i), h.value(i));
                }
                return new HttpResponse(response.code(), bytes, respHeaders);
            }
        } catch (IOException e) {
            throw new HttpTransportException("HTTP 传输失败: " + method + " " + url, e);
        } catch (IllegalArgumentException e) {
            throw new HttpTransportException("HTTP 请求构建失败（URL/参数非法）: " + method + " " + url, e);
        }
    }

    /** GET（默认超时）。 */
    public static HttpResponse get(String url, Map<String, String> headers) {
        return execute("GET", url, headers, null, null,
                DEFAULT_CONNECT_MILLIS, DEFAULT_READ_MILLIS, DEFAULT_WRITE_MILLIS);
    }

    /** GET（自定义超时）。 */
    public static HttpResponse get(String url, Map<String, String> headers,
                                   long connectMillis, long readMillis, long writeMillis) {
        return execute("GET", url, headers, null, null, connectMillis, readMillis, writeMillis);
    }

    /**
     * GET 二进制下载（版式文件等字节场景，自定义超时）。
     *
     * <p>与文本 {@link #get} 的区别只在调用方取值方式（{@code getRawBytes()}），
     * 传输行为完全一致：非 2xx 也正常返回，由调用方按业务判定成败。</p>
     */
    public static HttpResponse getBinary(String url, Map<String, String> headers,
                                         long connectMillis, long readMillis, long writeMillis) {
        return execute("GET", url, headers, null, null, connectMillis, readMillis, writeMillis);
    }

    /** POST 原始字节（自定义超时）。 */
    public static HttpResponse postBytes(String url, Map<String, String> headers,
                                         String mediaType, byte[] body,
                                         long connectMillis, long readMillis, long writeMillis) {
        return execute("POST", url, headers, mediaType, body, connectMillis, readMillis, writeMillis);
    }

    /** POST 文本（UTF-8，自定义超时）。 */
    public static HttpResponse postText(String url, Map<String, String> headers,
                                        String mediaType, String body,
                                        long connectMillis, long readMillis, long writeMillis) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        return execute("POST", url, headers, mediaType, bytes, connectMillis, readMillis, writeMillis);
    }

    /** POST 表单（application/x-www-form-urlencoded，自定义超时）。 */
    public static HttpResponse postForm(String url, Map<String, String> headers, String form,
                                        long connectMillis, long readMillis, long writeMillis) {
        return postText(url, headers, FORM, form, connectMillis, readMillis, writeMillis);
    }
}
