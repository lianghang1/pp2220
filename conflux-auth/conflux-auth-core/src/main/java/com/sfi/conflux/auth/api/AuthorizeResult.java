package com.sfi.conflux.auth.api;

import java.util.Map;

/**
 * 授权入口产出：provider 按需填字段，调用方按字段有无判断形态，框架不做枚举归类。
 *
 * <p>三种字段可组合、可扩展；未来新增形态加字段即可，不动现有接口。</p>
 *
 * @author lianghang
 */
public class AuthorizeResult {

    /** 非空 = 浏览器跳转型（REDIRECT） */
    private String authorizeUrl;

    /** 非空 = 二维码型（QRCODE） */
    private String qrContent;

    /** 非空 = 前端 SDK 型（JSAPI） */
    private Map<String, Object> jsApiParams;

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public AuthorizeResult setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
        return this;
    }

    public String getQrContent() {
        return qrContent;
    }

    public AuthorizeResult setQrContent(String qrContent) {
        this.qrContent = qrContent;
        return this;
    }

    public Map<String, Object> getJsApiParams() {
        return jsApiParams;
    }

    public AuthorizeResult setJsApiParams(Map<String, Object> jsApiParams) {
        this.jsApiParams = jsApiParams;
        return this;
    }
}
