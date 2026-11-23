package com.sfi.conflux.pay.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道回调应答：使用方将 body 原样写回渠道响应即可。
 *
 * <p>statusCode 缺省 200；渠道要求的应答报文（如 "success"、JSON、XML）
 * 由各渠道的回调处理器生成，使用方不感知格式差异。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ChannelCallbackReply implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 建议的 HTTP 状态码（缺省 200） */
    private int statusCode = 200;
    /** 应答报文（原样写回渠道） */
    private String body;

    /** 构建应答。 */
    public static ChannelCallbackReply of(int statusCode, String body) {
        return new ChannelCallbackReply().setStatusCode(statusCode).setBody(body);
    }
}
