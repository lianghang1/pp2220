package com.sfi.conflux.notify.channels.support;

import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 通知配置 → 平台统一凭证映射（通知域适配层）：attr 里按 wxjava 原生键名取凭证，映射为平台层凭证。
 *
 * <p>遵循契约 C2：appId/corpId/secret 只有 wxjava 认识，框架不解释其语义，一律放 attr；
 * 本映射是通知域与平台层之间唯一的一次字段适配，平台层对具体键名零感知（仅纳入缓存指纹）。</p>
 *
 * @author lianghang
 */
public final class WxCredentialMapper {

    /** 小程序/公众号：attr.appId + attr.secret */
    public static PlatformCredential appIdSecret(NotificationConfig config) {
        return PlatformCredential.of(attr(config, "appId"), attr(config, "secret"), config.getAttr());
    }

    /** 企业微信：attr.corpId → appId，attr.corpSecret → secret，agentId 由 attrs 透传 */
    public static PlatformCredential corp(NotificationConfig config) {
        return PlatformCredential.of(attr(config, "corpId"), attr(config, "corpSecret"), config.getAttr());
    }

    private static String attr(NotificationConfig config, String key) {
        return config.getAttr() == null ? null : config.getAttr().get(key);
    }

    private WxCredentialMapper() {
    }
}
