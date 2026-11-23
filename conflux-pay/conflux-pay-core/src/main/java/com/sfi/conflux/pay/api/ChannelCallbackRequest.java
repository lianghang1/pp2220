package com.sfi.conflux.pay.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道回调原始请求：使用方在自己的回调入口收集后交给网关处理。
 *
 * <p>使用方 Controller 收到渠道通知后，将原始报文（body / 表单参数 / 查询参数 /
 * 请求头）原样填入本对象，调用 {@code paymentGateway.handleCallback(request)}，
 * 再将返回的 {@link ChannelCallbackReply} 写回响应。框架不感知 HTTP 细节。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道编码（决定由哪个渠道的回调处理器解析） */
    private String channelCode;
    /** 请求头（渠道验签常用，如签名、时间戳、随机串） */
    private Map<String, String> headers;
    /** URL 查询参数 */
    private Map<String, String> queryParams;
    /** 表单参数（application/x-www-form-urlencoded 场景，如支付宝异步通知） */
    private Map<String, String> formParams;
    /** 原始请求体（JSON/XML/文本场景） */
    private String body;
}
