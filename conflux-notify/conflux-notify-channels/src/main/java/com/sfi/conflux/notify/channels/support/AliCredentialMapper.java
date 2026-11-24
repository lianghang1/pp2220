package com.sfi.conflux.notify.channels.support;

import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 通知配置 → 平台统一凭证映射（支付宝）：attr.appId = 应用ID、attr.privateKey = 应用私钥，
 * 网关/公钥/证书/签名类型等全部留在 attrs，由平台层 AlipayClients 按原生键名消费。
 *
 * <p>遵循契约 C2：这些键名只有支付宝 SDK 认识，框架不解释语义，一律放 attr。</p>
 *
 * @author lianghang
 */
public final class AliCredentialMapper {

    public static PlatformCredential of(NotificationConfig config) {
        return PlatformCredential.of(
                attr(config, "appId"),
                attr(config, "privateKey"),
                config.getAttr());
    }

    private static String attr(NotificationConfig config, String key) {
        return config.getAttr() == null ? null : config.getAttr().get(key);
    }

    private AliCredentialMapper() {
    }
}
