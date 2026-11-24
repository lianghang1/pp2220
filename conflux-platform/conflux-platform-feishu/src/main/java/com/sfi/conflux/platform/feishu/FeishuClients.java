package com.sfi.conflux.platform.feishu;

import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.sfi.conflux.platform.core.ClientCache;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 飞书平台客户端薄门面：以统一凭证换取飞书官方原生 {@link Client}。
 *
 * <p>飞书只有一种 client 类型（自建应用 / 商店应用由凭证 attrs.appType 决定），
 * access_token 的获取与缓存全部由官方 SDK 内部管理，平台层不重复实现 TokenStore。
 * 门面只返回原生 client，不封任何能力——调用方拿到后直接
 * {@code client.im().message().create(req)} 即可使用官方 SDK 全部接口。</p>
 *
 * @author lianghang
 */
public final class FeishuClients {

    private final ClientCache cache = new ClientCache();

    /**
     * 取原生飞书 Client。
     *
     * @param c 统一凭证（appId=应用ID、secret=应用密钥，attrs 含 domain/appType）
     * @return 飞书官方原生 client
     */
    public Client client(PlatformCredential c) {
        String fingerprint = c.appId + "|" + c.secret + "|" + c.attrs;
        return cache.get("feishu:client", fingerprint, () -> build(c));
    }

    private static Client build(PlatformCredential c) {
        Client.Builder builder = Client.newBuilder(c.appId, c.secret);
        if ("lark".equalsIgnoreCase(attr(c, "domain"))) {
            builder.openBaseUrl(BaseUrlEnum.LarkSuite);
        } else {
            builder.openBaseUrl(BaseUrlEnum.FeiShu);
        }
        if ("marketplace".equalsIgnoreCase(attr(c, "appType"))) {
            builder.marketplaceApp();
        }
        return builder.build();
    }

    private static String attr(PlatformCredential c, String key) {
        return c.attrs == null ? null : c.attrs.get(key);
    }
}
