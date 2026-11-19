package io.github.lianghang1.conflux.sms.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信渠道配置值对象：承载「appId + 渠道 + 配置键值」。
 *
 * <p>与支付域 {@code ChannelConfig}、发票域 {@code ChannelConfig} 同层次的东西，
 * 只是短信没有自有渠道实现，{@code attrs} 的键名直接对齐 sms4j Config 字段
 * （统一凭证四件套 + 厂商特有字段）。由 {@link SmsConfigProvider} 产出，
 * 交给装载器构建 sms4j 的 SmsBlend。</p>
 *
 * @author lianghang
 */
public final class SmsChannelConfig {

    private final String appId;
    private final String channel;
    private final Map<String, String> attrs;

    public SmsChannelConfig(String appId, String channel, Map<String, String> attrs) {
        this.appId = appId;
        this.channel = channel;
        this.attrs = Collections.unmodifiableMap(
                attrs == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(attrs));
    }

    public String getAppId() {
        return appId;
    }

    public String getChannel() {
        return channel;
    }

    public Map<String, String> getAttrs() {
        return attrs;
    }

    @Override
    public String toString() {
        return "SmsChannelConfig{appId='" + appId + "', channel='" + channel + "'}";
    }
}
