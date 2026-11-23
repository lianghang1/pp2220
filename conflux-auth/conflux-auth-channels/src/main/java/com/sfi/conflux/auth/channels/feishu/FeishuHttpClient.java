package com.sfi.conflux.auth.channels.feishu;

import java.util.Map;

/**
 * 飞书 HTTP 客户端抽象：只暴露渠道需要的 GET / POST(JSON) 两种原始调用。
 *
 * <p>飞书登录走标准 HTTP + JSON，无官方 SDK 依赖（官方 SDK 是服务端业务 API SDK，
 * 不覆盖第三方 OAuth 登录，且 20MB/13 依赖/CVE 告警，故不引）；抽象成接口是为了
 * 测试可注入 mock。默认实现 {@link FeishuDefaultHttpClient} 复用 conflux-common 的
 * HttpTransport，不在本模块重复造 HTTP / JSON 轮子。</p>
 *
 * @author lianghang
 */
public interface FeishuHttpClient {

    /** GET，返回响应体文本（非 2xx 由调用方按飞书协议判定）。 */
    String get(String url, Map<String, String> headers);

    /** POST JSON，返回响应体文本。 */
    String postJson(String url, Map<String, String> headers, String jsonBody);
}
