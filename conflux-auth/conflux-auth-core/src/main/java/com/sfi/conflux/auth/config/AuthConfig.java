package com.sfi.conflux.auth.config;

import java.util.Map;

/**
 * 登录渠道配置：公共字段一等化 + attr 承载平台特有参数（与四域一致）。
 *
 * @author lianghang
 */
public class AuthConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope;

    /** 平台特有：私钥、证书、corpId、agentId 等，参数名不写死 */
    private Map<String, String> attr;

    public String getClientId() {
        return clientId;
    }

    public AuthConfig setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public AuthConfig setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public AuthConfig setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public String getScope() {
        return scope;
    }

    public AuthConfig setScope(String scope) {
        this.scope = scope;
        return this;
    }

    public Map<String, String> getAttr() {
        return attr;
    }

    public AuthConfig setAttr(Map<String, String> attr) {
        this.attr = attr;
        return this;
    }
}
