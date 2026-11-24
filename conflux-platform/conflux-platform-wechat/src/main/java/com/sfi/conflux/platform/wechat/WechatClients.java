package com.sfi.conflux.platform.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import com.sfi.conflux.platform.core.ClientCache;
import com.sfi.conflux.platform.core.PlatformCredential;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;

/**
 * 微信平台客户端薄门面：以统一凭证换取 wxjava 原生 service，调用方拿到后直接调 SDK 接口。
 *
 * <p>不封装 SDK 的 40+ 能力（OCR/二维码/订阅消息…），不包装 client，门面只做「凭证 → 原生 client」。
 * 内部复用平台层共用的 {@link ClientCache}：同一凭证复用实例，凭证变更（指纹变化）自动重建。
 * 纯内存 ConfigStorage，token 由各 SDK 实例自管理，平台层不碰 token 存储。</p>
 *
 * @author lianghang
 */
public final class WechatClients {

    private final ClientCache cache = new ClientCache();

    /** 小程序：appId + secret（wxjava 方法名 setAppid，小写 d） */
    public WxMaService ma(PlatformCredential c) {
        return cache.get("wechat:ma", fp(c), () -> {
            WxMaDefaultConfigImpl cfg = new WxMaDefaultConfigImpl();
            cfg.setAppid(c.appId);
            cfg.setSecret(c.secret);
            WxMaService svc = new WxMaServiceImpl();
            svc.setWxMaConfig(cfg);
            return svc;
        });
    }

    /** 公众号 / 开放平台：appId + secret */
    public WxMpService mp(PlatformCredential c) {
        return cache.get("wechat:mp", fp(c), () -> {
            WxMpDefaultConfigImpl cfg = new WxMpDefaultConfigImpl();
            cfg.setAppId(c.appId);
            cfg.setSecret(c.secret);
            WxMpService svc = new WxMpServiceImpl();
            svc.setWxMpConfigStorage(cfg);
            return svc;
        });
    }

    /** 企业微信：corpId(appId) + corpSecret(secret) + agentId(attrs) */
    public WxCpService cp(PlatformCredential c) {
        Integer agentId = agentId(c);
        String fingerprint = fp(c) + "|agent=" + agentId;
        return cache.get("wechat:cp", fingerprint, () -> {
            WxCpDefaultConfigImpl cfg = new WxCpDefaultConfigImpl();
            cfg.setCorpId(c.appId);
            cfg.setCorpSecret(c.secret);
            if (agentId != null) {
                cfg.setAgentId(agentId);
            }
            WxCpService svc = new WxCpServiceImpl();
            svc.setWxCpConfigStorage(cfg);
            return svc;
        });
    }

    /** 指纹：appId|secret + attrs，凭证或平台特有参数变化即重建 */
    private static String fp(PlatformCredential c) {
        return c.appId + "|" + c.secret + "|" + c.attrs;
    }

    /** 企微 agentId 从 attrs 读取（agentId / agentid 两种写法兼容），无则 null */
    private static Integer agentId(PlatformCredential c) {
        String v = c.attrs.get("agentId");
        if (v == null) {
            v = c.attrs.get("agentid");
        }
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
