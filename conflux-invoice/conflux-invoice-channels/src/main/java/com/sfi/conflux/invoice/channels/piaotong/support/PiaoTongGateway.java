package com.sfi.conflux.invoice.channels.piaotong.support;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.http.HttpResponse;
import com.sfi.conflux.common.http.HttpTransport;
import com.sfi.conflux.common.http.HttpTransportException;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.invoice.channels.piaotong.PiaoTongParams;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;

import java.util.Collections;
import java.util.Map;

/**
 * 票通网关调用封装：业务报文 → 3DES 加密 → RSA 签名信封 → HTTP POST →
 * 验签解密 → 业务报文 JSON 树。
 *
 * <p>传输层只用 common 的 {@link HttpTransport}；协议细节封闭在本包内。</p>
 *
 * @author lianghang
 */
public final class PiaoTongGateway {

    private final PiaoTongParams params;

    public PiaoTongGateway(PiaoTongParams params) {
        this.params = params;
    }

    /** 调用接口：{@code path} 为 {@link com.sfi.conflux.invoice.channels.piaotong.PiaoTongSpec} 中的路径常量。 */
    public PiaoTongResponse call(String path, Map<String, Object> businessBody) {
        String fullUrl = params.url(path);
        String businessJson = PiaoTongCodec.toJson(businessBody);
        Map<String, Object> envelope = PiaoTongEnvelope.build(params, businessJson);
        try {
            HttpResponse response = HttpTransport.postText(fullUrl, Collections.<String, String>emptyMap(),
                    "application/json; charset=utf-8", JsonUtils.toJson(envelope),
                    params.getTimeoutMillis(), params.getTimeoutMillis(),
                    params.getTimeoutMillis());
            if (!response.isSuccessful()) {
                throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                        "票通 HTTP 状态异常: path={}, status={}, body={}",
                        path, response.getStatusCode(), response.getBody());
            }
            return PiaoTongEnvelope.parse(response.getBody(), params, true);
        } catch (HttpTransportException e) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CALL_FAILED,
                    "票通传输失败: " + e.getMessage(), e);
        }
    }
}
