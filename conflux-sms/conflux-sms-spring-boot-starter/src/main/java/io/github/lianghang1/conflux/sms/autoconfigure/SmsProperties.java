package io.github.lianghang1.conflux.sms.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关 starter 配置（{@code conflux.sms.*}）。
 *
 * <p>与支付域 PayGatewayProperties 完全同构的「应用 → 渠道 → 键值对」结构。
 * 统一基础键（如 notifyUrl）在短信域暂不需要，全部个性键走 attrs。</p>
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

    /** 单渠道配置：渠道个性键值包。 */
    public static class ChannelProperties {
        /** 渠道个性键值包（键名由各渠道定义，经 SmsChannelProvider.parseParams 解析）。 */
        private Map<String, String> attrs = new LinkedHashMap<String, String>();

        public Map<String, String> getAttrs() {
            return attrs;
        }

        public void setAttrs(Map<String, String> attrs) {
            this.attrs = attrs;
        }
    }
}
