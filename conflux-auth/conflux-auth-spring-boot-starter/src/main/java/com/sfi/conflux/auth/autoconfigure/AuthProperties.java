package com.sfi.conflux.auth.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录网关 starter 配置（{@code conflux.auth.*}）。
 *
 * <p>与支付/短信域同构的「configId → 渠道编码 → 配置」结构：第一层 key 为 configId，
 * 第二层 key 为渠道编码（wechat_open / alipay_web / dingtalk_qr …）。OAuth 公共字段
 * （clientId/clientSecret/redirectUri/scope）提到外层一等字段，平台特有键（私钥、
 * 证书、corpId、agentId、端点版本等）按平台 SDK 字段名放在 attr。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "conflux.auth")
public class AuthProperties {

    /** 应用 → 渠道配置：第一层 key 为 configId，第二层 key 为渠道编码。 */
    private Map<String, Map<String, ChannelProperties>> configs =
            new LinkedHashMap<String, Map<String, ChannelProperties>>();

    public Map<String, Map<String, ChannelProperties>> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, Map<String, ChannelProperties>> configs) {
        this.configs = configs;
    }

    /** 单渠道配置：OAuth 公共字段 + 平台个性键值包。 */
    public static class ChannelProperties {

        /** OAuth 应用标识（微信 appId / 支付宝 app_id / 钉钉 clientId …，对外统一叫 clientId） */
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope;

        /** 平台个性键值包（私钥/证书/corpId/agentId/端点版本等，参数名按平台约定，不写死）。 */
        private Map<String, String> attr = new LinkedHashMap<String, String>();

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Map<String, String> getAttr() {
            return attr;
        }

        public void setAttr(Map<String, String> attr) {
            this.attr = attr;
        }
    }
}
