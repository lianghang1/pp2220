package com.sfi.conflux.auth.channels.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.platform.core.PlatformCredential;
import com.sfi.conflux.platform.wechat.WechatClients;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.mp.api.WxMpService;

/**
 * 微信系 SDK 客户端持有器（登录域适配层）：把 {@link AuthConfig} 映射为统一凭证，
 * 取 client 的动作委托平台层 {@link WechatClients}。
 *
 * <p>公众号/开放平台扫码用 WxMpService，小程序用 WxMaService，企业微信用 WxCpService——
 * 三种 service 的构造、缓存、token 管理全部下沉到平台层门面，本类只做「AuthConfig →
 * PlatformCredential」字段映射（clientId/clientSecret→appId/secret，attr 原样透传）。
 * 保留原方法签名，渠道实现与既有测试零改动。</p>
 *
 * @author lianghang
 */
public class WxClientHolder {

    private final WechatClients clients = new WechatClients();

    /** 兼容别名：早期 PoC 用 service()，等价于 mp() */
    public WxMpService service(AuthConfig config) {
        return mp(config);
    }

    /** 公众号 / 开放平台扫码用（appId + secret） */
    public WxMpService mp(AuthConfig config) {
        return clients.mp(credential(config));
    }

    /** 小程序用（appId + secret） */
    public WxMaService ma(AuthConfig config) {
        return clients.ma(credential(config));
    }

    /** 企业微信用（corpId + corpSecret + agentId） */
    public WxCpService cp(AuthConfig config) {
        return clients.cp(credential(config));
    }

    /** AuthConfig → 平台统一凭证：clientId→appId、clientSecret→secret、attr 原样透传（含 agentId） */
    private static PlatformCredential credential(AuthConfig config) {
        return PlatformCredential.of(config.getClientId(), config.getClientSecret(), config.getAttr());
    }
}
