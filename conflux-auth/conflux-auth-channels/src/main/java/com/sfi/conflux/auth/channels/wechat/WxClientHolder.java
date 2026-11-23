package com.sfi.conflux.auth.channels.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import com.sfi.conflux.auth.config.AuthConfig;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信系 SDK 客户端持有器：按应用标识缓存 wxjava 的三种 service。
 *
 * <p>公众号/开放平台扫码用 {@link WxMpService}（weixin-java-mp），小程序用
 * {@link WxMaService}（weixin-java-miniapp），企业微信用 {@link WxCpService}（weixin-java-cp）。
 * 三者都用 wxjava 的纯内存 ConfigStorage，零 Redis 依赖——token 由 SDK 实例内自管理。</p>
 *
 * @author lianghang
 */
public class WxClientHolder {

    private final ConcurrentHashMap<String, WxMpService> mpServices = new ConcurrentHashMap<String, WxMpService>();
    private final ConcurrentHashMap<String, WxMaService> maServices = new ConcurrentHashMap<String, WxMaService>();
    private final ConcurrentHashMap<String, WxCpService> cpServices = new ConcurrentHashMap<String, WxCpService>();
    private final ConcurrentHashMap<String, String> secrets = new ConcurrentHashMap<String, String>();

    /** 兼容别名：早期 PoC 用 service()，等价于 mp() */
    public WxMpService service(AuthConfig config) {
        return mp(config);
    }

    /** 公众号 / 开放平台扫码用（appId + secret） */
    public WxMpService mp(AuthConfig config) {
        String key = "mp:" + config.getClientId();
        if (hitSecret(key, config)) {
            WxMpService hit = mpServices.get(key);
            if (hit != null) {
                return hit;
            }
        }
        WxMpDefaultConfigImpl storage = new WxMpDefaultConfigImpl();
        storage.setAppId(config.getClientId());
        storage.setSecret(config.getClientSecret());
        WxMpService built = new WxMpServiceImpl();
        built.setWxMpConfigStorage(storage);
        mpServices.put(key, built);
        refreshSecret(key, config);
        return built;
    }

    /** 小程序用（appId + secret） */
    public WxMaService ma(AuthConfig config) {
        String key = "ma:" + config.getClientId();
        if (hitSecret(key, config)) {
            WxMaService hit = maServices.get(key);
            if (hit != null) {
                return hit;
            }
        }
        WxMaDefaultConfigImpl storage = new WxMaDefaultConfigImpl();
        storage.setAppid(config.getClientId());
        storage.setSecret(config.getClientSecret());
        WxMaService built = new WxMaServiceImpl();
        built.setWxMaConfig(storage);
        maServices.put(key, built);
        refreshSecret(key, config);
        return built;
    }

    /** 企业微信用（corpId + corpSecret + agentId） */
    public WxCpService cp(AuthConfig config) {
        String key = "cp:" + config.getClientId();
        if (hitSecret(key, config)) {
            WxCpService hit = cpServices.get(key);
            if (hit != null) {
                return hit;
            }
        }
        WxCpDefaultConfigImpl storage = new WxCpDefaultConfigImpl();
        storage.setCorpId(config.getClientId());
        storage.setCorpSecret(config.getClientSecret());
        Integer agentId = WxSupport.agentId(config);
        if (agentId != null) {
            storage.setAgentId(agentId);
        }
        WxCpService built = new WxCpServiceImpl();
        built.setWxCpConfigStorage(storage);
        cpServices.put(key, built);
        refreshSecret(key, config);
        return built;
    }

    private boolean hitSecret(String key, AuthConfig config) {
        String cached = secrets.get(key);
        return cached != null && cached.equals(config.getClientSecret());
    }

    private void refreshSecret(String key, AuthConfig config) {
        secrets.put(key, config.getClientSecret());
    }
}
