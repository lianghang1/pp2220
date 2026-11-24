package com.sfi.conflux.platform.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.sfi.conflux.platform.core.PlatformCredential;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.mp.api.WxMpService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * WechatClients 门面单测：凭证正确写入原生 config、同凭证复用、凭证变更重建、agentId 生效。
 *
 * @author lianghang
 */
class WechatClientsTest {

    private final WechatClients clients = new WechatClients();

    @Test
    void maBuildsNativeServiceWithCredential() {
        PlatformCredential c = PlatformCredential.of("wx-app", "wx-sec");
        WxMaService svc = clients.ma(c);

        assertEquals("wx-app", svc.getWxMaConfig().getAppid());
        assertEquals("wx-sec", svc.getWxMaConfig().getSecret());
        assertSame(svc, clients.ma(c), "同凭证应复用同一实例");
    }

    @Test
    void mpBuildsNativeServiceWithCredential() {
        PlatformCredential c = PlatformCredential.of("mp-app", "mp-sec");
        WxMpService svc = clients.mp(c);

        assertEquals("mp-app", svc.getWxMpConfigStorage().getAppId());
        assertEquals("mp-sec", svc.getWxMpConfigStorage().getSecret());
        assertSame(svc, clients.mp(c));
    }

    @Test
    void cpBuildsNativeServiceWithAgentId() {
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("agentId", "1002");
        PlatformCredential c = PlatformCredential.of("cp-corp", "cp-sec", attrs);
        WxCpService svc = clients.cp(c);

        assertEquals("cp-corp", svc.getWxCpConfigStorage().getCorpId());
        assertEquals("cp-sec", svc.getWxCpConfigStorage().getCorpSecret());
        assertEquals(Integer.valueOf(1002), svc.getWxCpConfigStorage().getAgentId());
        assertSame(svc, clients.cp(c));
    }

    @Test
    void changedCredentialRebuilds() {
        WxMaService a = clients.ma(PlatformCredential.of("app", "sec1"));
        WxMaService b = clients.ma(PlatformCredential.of("app", "sec2"));

        assertNotSame(a, b);
        assertEquals("sec1", a.getWxMaConfig().getSecret());
        assertEquals("sec2", b.getWxMaConfig().getSecret());
    }

    @Test
    void changedAgentIdRebuildsCp() {
        Map<String, String> attrs1 = new LinkedHashMap<String, String>();
        attrs1.put("agentId", "1");
        Map<String, String> attrs2 = new LinkedHashMap<String, String>();
        attrs2.put("agentId", "2");

        WxCpService a = clients.cp(PlatformCredential.of("corp", "sec", attrs1));
        WxCpService b = clients.cp(PlatformCredential.of("corp", "sec", attrs2));

        assertNotSame(a, b);
        assertEquals(Integer.valueOf(1), a.getWxCpConfigStorage().getAgentId());
        assertEquals(Integer.valueOf(2), b.getWxCpConfigStorage().getAgentId());
    }
}
