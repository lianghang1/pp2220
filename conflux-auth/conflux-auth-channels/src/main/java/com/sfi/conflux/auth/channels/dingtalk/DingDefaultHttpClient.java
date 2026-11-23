package com.sfi.conflux.auth.channels.dingtalk;

import com.sfi.conflux.common.http.HttpResponse;
import com.sfi.conflux.common.http.HttpTransport;

import java.util.Map;

/**
 * 钉钉 HTTP 默认实现：复用 conflux-common 的传输层，零第三方 SDK、零重复造轮子。
 *
 * @author lianghang
 */
public class DingDefaultHttpClient implements DingHttpClient {

    @Override
    public String get(String url, Map<String, String> headers) {
        HttpResponse resp = HttpTransport.get(url, headers);
        return resp.getBody();
    }

    @Override
    public String postJson(String url, Map<String, String> headers, String jsonBody) {
        HttpResponse resp = HttpTransport.postText(url, headers, HttpTransport.JSON, jsonBody,
                10000L, 30000L, 10000L);
        return resp.getBody();
    }

    @Override
    public String postForm(String url, Map<String, String> headers, String formBody) {
        HttpResponse resp = HttpTransport.postForm(url, headers, formBody,
                10000L, 30000L, 10000L);
        return resp.getBody();
    }
}
