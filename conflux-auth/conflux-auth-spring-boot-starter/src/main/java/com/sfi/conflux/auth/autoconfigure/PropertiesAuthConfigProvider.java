package com.sfi.conflux.auth.autoconfigure;

import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.exception.AuthErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于配置文件的登录配置提供者（默认实现）：与支付域 {@code PropertiesChannelConfigProvider}、
 * 短信域 {@code PropertiesSmsConfigProvider} 同构，按
 * {@code conflux.auth.configs.<configId>.<channelCode>} 读取配置。
 *
 * <p>OAuth 公共字段为外层一等字段，平台特有键按平台约定声明在 attr；
 * 查无配置抛 CONFIG_MISSING。使用方自定义同类型 {@link AuthConfigProvider} Bean 可覆盖。</p>
 *
 * @author lianghang
 */
public class PropertiesAuthConfigProvider implements AuthConfigProvider {

    private final AuthProperties properties;

    public PropertiesAuthConfigProvider(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public AuthConfig getConfig(String configId, String channelCode) {
        Map<String, AuthProperties.ChannelProperties> channels = properties.getConfigs().get(configId);
        AuthProperties.ChannelProperties props = channels == null ? null : channels.get(channelCode);
        if (props == null) {
            throw AuthErrorCode.CONFIG_MISSING.of(
                    "登录渠道配置缺失: configId=" + configId + ", channelCode=" + channelCode);
        }
        return toConfig(configId, channelCode, props);
    }

    /** 遍历全部已配置渠道（供启动期预热校验，fail-fast），key 为 configId#channelCode。 */
    @Override
    public Map<String, AuthConfig> allConfigs() {
        Map<String, AuthConfig> result = new LinkedHashMap<String, AuthConfig>();
        for (Map.Entry<String, Map<String, AuthProperties.ChannelProperties>> configEntry
                : properties.getConfigs().entrySet()) {
            String configId = configEntry.getKey();
            Map<String, AuthProperties.ChannelProperties> channels = configEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, AuthProperties.ChannelProperties> channelEntry : channels.entrySet()) {
                result.put(configId + "#" + channelEntry.getKey(),
                        toConfig(configId, channelEntry.getKey(), channelEntry.getValue()));
            }
        }
        return result;
    }

    private static AuthConfig toConfig(String configId, String channelCode, AuthProperties.ChannelProperties props) {
        return new AuthConfig()
                .setConfigId(configId)
                .setChannelCode(channelCode)
                .setClientId(props.getClientId())
                .setClientSecret(props.getClientSecret())
                .setRedirectUri(props.getRedirectUri())
                .setScope(props.getScope())
                .setAttr(props.getAttr());
    }
}
