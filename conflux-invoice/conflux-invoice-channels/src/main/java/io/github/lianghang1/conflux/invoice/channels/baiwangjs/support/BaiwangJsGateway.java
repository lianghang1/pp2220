package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.http.HttpTransportException;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsParams;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 百旺金穗云网关调用封装：签名 → Base64 报文 → HTTP POST → 同步响应解析。
 *
 * <p>统一收口「每次调用都要重新算签名」这件事：Date 为当前 GMT、nonce 每次随机，
 * 因此签名不能复用；渠道能力实现只需准备业务 body，其余由本类完成。
 * 传输层只用 common 的 {@link HttpTransport}，渠道包不直接依赖 OkHttp。</p>
 *
 * @author lianghang
 */
public final class BaiwangJsGateway {

    private final BaiwangJsParams params;

    public BaiwangJsGateway(BaiwangJsParams params) {
        this.params = params;
    }

    /** 按版本选择路径并调用；返回已解析的同步响应。 */
    public BaiwangJsResponse call(String pathKey, String v2Path, String v1Path,
                                  Map<String, Object> body) {
        String path = params.path(pathKey, v2Path, v1Path);
        String fullUrl = params.url(path);
        String requestLine = params.requestLine(fullUrl);
        String encoded = BaiwangJsCodec.encodeRequest(body);
        Map<String, String> headers = new LinkedHashMap<String, String>(
                BaiwangJsSigner.buildHeaders(params, requestLine,
                        ZonedDateTime.now(), null));

        try {
            HttpResponse response = HttpTransport.postText(fullUrl, headers,
                    "application/json; charset=utf-8", encoded,
                    params.getTimeoutMillis(), params.getTimeoutMillis(),
                    params.getTimeoutMillis());
            if (!response.isSuccessful()) {
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "百旺金穗云 HTTP 状态异常: path={}, status={}, body={}",
                        path, response.getStatusCode(), response.getBody());
            }
            BaiwangJsResponse parsed = BaiwangJsCodec.parseResponse(response.getBody());
            return parsed;
        } catch (HttpTransportException e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "百旺金穗云传输失败: " + e.getMessage(), e);
        }
    }

    /**
     * 3.1.1 查询企业唯一标识（配置期工具，仅调用一次）。
     *
     * <p>框架不自动调用，避免启动期产生外部依赖；使用方在配置 {@code qyId} 前调用本方法，
     * 把返回的 qyId 写入 {@code attrs.qyId}。返回 null 表示渠道未给出（code≠0）。</p>
     */
    public String queryUniqueSign(String sellerName, String sellerTaxNo) {
        Map<String, Object> body = BaiwangJsCodec.uniqueSignBody(sellerName, sellerTaxNo);
        BaiwangJsResponse response = call("uniqueSignPath",
                io.github.lianghang1.conflux.invoice.channels.baiwangjs.BaiwangJsSpec
                        .QUERY_UNIQUE_SIGN, null, body);
        if (!response.isAccepted()) {
            return null;
        }
        return response.dataText("qyId");
    }

    /** 读取同步响应中 data 下的字段（兼容 data / Data）。 */
    public static String dataText(BaiwangJsResponse response, String... names) {
        return response == null ? null : response.dataText(names);
    }
}
