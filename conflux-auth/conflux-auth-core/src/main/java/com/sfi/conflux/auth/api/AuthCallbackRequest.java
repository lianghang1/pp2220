package com.sfi.conflux.auth.api;

import java.util.Collections;
import java.util.Map;

/**
 * 回调入参：原始参数全量保留，不假定任何参数名。
 *
 * <p>框架绝不写死 code/state 的字面字段名——各平台叫法千差万别（code / auth_code /
 * authorization_code / tmp_code / js_code / corpId 签名串…），一律由 provider 按自己的名字取。</p>
 *
 * @author lianghang
 */
public class AuthCallbackRequest {

    /** 原始参数全量 Map（query + form + body JSON 展平），一个不丢，验签也用它 */
    private final Map<String, String> params;

    /** HTTP 上下文：部分平台验签要读 header / method / 原始 body */
    private final Map<String, String> headers;
    private final String httpMethod;
    private final String rawBody;

    public AuthCallbackRequest(Map<String, String> params, Map<String, String> headers,
                                String httpMethod, String rawBody) {
        this.params = params == null ? Collections.<String, String>emptyMap() : params;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : headers;
        this.httpMethod = httpMethod;
        this.rawBody = rawBody;
    }

    /** 便捷构造：只有参数、没有 HTTP 上下文的场景 */
    public AuthCallbackRequest(Map<String, String> params) {
        this(params, null, null, null);
    }

    /** 按原始参数名取值——这是 provider 取票据的唯一方式 */
    public String param(String name) {
        return params.get(name);
    }

    /** 取第一个非空的参数，兼容同平台多形态的参数名差异（如 code / auth_code） */
    public String firstParam(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            String v = params.get(name);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String getRawBody() {
        return rawBody;
    }

    public String getHttpMethod() {
        return httpMethod;
    }
}
