package io.github.lianghang1.conflux.sms.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关 starter 配置（{@code conflux.sms.*}）。
 *
 * <p>与支付域 PayGatewayProperties 同构的「应用 → 渠道 → 配置」结构。渠道编码
 * 直接采用 sms4j 的 supplier 标识（alibaba/tencent/huawei…）；统一凭证键提到外层，
 * 其余字段按 sms4j Config 字段名放在 attrs，装载期合并后交给 sms4j 官方工厂。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "conflux.sms")
public class SmsProperties {

    /** 应用 → 渠道配置：第一层 key 为 appId，第二层 key 为渠道编码。 */
    private Map<String, Map<String, ChannelProperties>> apps =
            new LinkedHashMap<String, Map<String, ChannelProperties>>();

    public Map<String, Map<String, ChannelProperties>> getApps() {
        return apps;
    }

    public void setApps(Map<String, Map<String, ChannelProperties>> apps) {
        this.apps = apps;
    }

    /** 单渠道配置：统一凭证字段 + 渠道个性键值包。 */
    public static class ChannelProperties {

        // ---- 统一键：sms4j BaseConfig 公共字段中跨渠道同名同义的凭证四件套 ----
        private String accessKeyId;
        private String accessKeySecret;
        private String signature;
        private String templateId;

        /** 渠道个性键值包（厂商特有键名，如 sdkAppId/sender/signId，经 parseParams 解析）。 */
        private Map<String, String> attrs = new LinkedHashMap<String, String>();

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public Map<String, String> getAttrs() {
            return attrs;
        }

        public void setAttrs(Map<String, String> attrs) {
            this.attrs = attrs;
        }
    }
}
