package com.sfi.conflux.platform.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一凭证载体：平台层对外一律用 appId / secret 叫法，屏蔽各域 clientId / appKey / accessKeyId 差异。
 *
 * <p>各域接入时做一次字段映射（auth clientId→appId、钉钉 appKey→appId），映射在调用方完成，
 * 平台层只认这一种凭证形态。attrs 承载平台特有参数（证书路径/gateway/agentId 等），
 * 平台层对 attrs 内容零感知，仅将其纳入缓存指纹以驱动重建。</p>
 *
 * @author lianghang
 */
public final class PlatformCredential {

    /** 统一应用标识（微信 appId / 钉钉 appKey 映射而来） */
    public final String appId;
    /** 统一密钥 */
    public final String secret;
    /** 平台特有参数（只读副本） */
    public final Map<String, String> attrs;

    private PlatformCredential(String appId, String secret, Map<String, String> attrs) {
        this.appId = appId;
        this.secret = secret;
        this.attrs = attrs == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(attrs));
    }

    /** 仅 appId + secret 的凭证 */
    public static PlatformCredential of(String appId, String secret) {
        return new PlatformCredential(appId, secret, null);
    }

    /** 带平台特有参数的凭证 */
    public static PlatformCredential of(String appId, String secret, Map<String, String> attrs) {
        return new PlatformCredential(appId, secret, attrs);
    }
}
