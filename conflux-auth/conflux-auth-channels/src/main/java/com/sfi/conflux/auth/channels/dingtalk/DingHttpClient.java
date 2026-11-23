package com.sfi.conflux.auth.channels.dingtalk;

import java.util.Map;

/**
 * 钉钉 HTTP 客户端抽象：只暴露渠道需要的 GET / POST(JSON) / POST(FORM) 三种原始调用。
 *
 * <p>钉钉走标准 HTTP + JSON，无官方 SDK 依赖，故在 channels 层自建一层薄封装；
 * 抽象成接口是为了测试可注入 mock，不依赖真实网络。默认实现
 * {@link DingDefaultHttpClient} 复用 conflux-common 的 HttpTransport + JsonUtils，
 * 不在本模块重复造 HTTP / JSON 轮子。</p>
 *
 * @author lianghang
 */
public interface DingHttpClient {

    /** GET，返回响应体文本（非 2xx 由调用方按钉钉协议判定）。 */
    String get(String url, Map<String, String> headers);

    /** POST JSON，返回响应体文本。 */
    String postJson(String url, Map<String, String> headers, String jsonBody);

    /** POST 表单（application/x-www-form-urlencoded），返回响应体文本。 */
    String postForm(String url, Map<String, String> headers, String formBody);
}
