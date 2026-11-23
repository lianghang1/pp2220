package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.api.AuthCallbackRequest;
import com.sfi.conflux.auth.api.AuthResult;
import com.sfi.conflux.auth.api.AuthUser;
import com.sfi.conflux.auth.api.AuthorizeResult;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.auth.config.AuthConfigProvider;
import com.sfi.conflux.auth.engine.AuthGateway;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.service.WxOAuth2Service;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;
import me.chanjar.weixin.mp.api.WxMpService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 微信全家桶 provider 契约测试：不依赖真实网络，stub 底层 wxjava。
 */
class WechatOpenAuthChannelTest {

    @Test
    void wechat_open_授权URL是qrconnect端点而非公众号authorize() {
        WechatOpenAuthChannel channel = new WechatOpenAuthChannel(new WxClientHolder());
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        AuthorizeResult result = channel.authorize(baseConfig(), null);
        String url = result.getAuthorizeUrl();

        // 核心回归断言：扫码必须是 qrconnect，不能是 mp 模块的 oauth2/authorize
        assertThat(url).contains("open.weixin.qq.com/connect/qrconnect");
        assertThat(url).doesNotContain("/connect/oauth2/authorize");
        assertThat(url).contains("scope=snsapi_login");
        assertThat(url).contains("appid=wx123");
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fa.com%2Fcb");
        assertThat(url).contains("state=");
    }

    @Test
    void wechat_open_code换token换userinfo并归一AuthUser() throws Exception {
        WxOAuth2AccessToken token = new WxOAuth2AccessToken();
        token.setOpenId("open-001");
        token.setUnionId("union-001");
        WxOAuth2UserInfo info = new WxOAuth2UserInfo();
        info.setOpenid("open-001");
        info.setNickname("张三");
        info.setHeadImgUrl("https://avatar/x.png");
        info.setSex(1);

        WxOAuth2Service oauth2 = mock(WxOAuth2Service.class);
        when(oauth2.getAccessToken("the-code")).thenReturn(token);
        when(oauth2.getUserInfo(eq(token), isNull())).thenReturn(info);

        WechatOpenAuthChannel noState = new WechatOpenAuthChannel(stubMpHolder(oauth2)) {
            @Override
            public boolean requireStateCheck() { return false; }
        };
        noState.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "the-code");
        AuthResult<AuthUser> result = noState.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        AuthUser user = result.getData();
        assertThat(user.getChannelCode()).isEqualTo("wechat_open");
        assertThat(user.getOpenId()).isEqualTo("open-001");
        assertThat(user.getUnionId()).isEqualTo("union-001");
        assertThat(user.getUsername()).isEqualTo("张三");
        assertThat(user.getAvatar()).isEqualTo("https://avatar/x.png");
        assertThat(user.getGender()).isEqualTo("M");
        assertThat(user.getRawUserInfo()).containsKey("nickname");
    }

    @Test
    void wechat_mp_授权URL走wxjava的oauth2authorize() {
        WxOAuth2Service oauth2 = mock(WxOAuth2Service.class);
        when(oauth2.buildAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx123"
                        + "&redirect_uri=" + inv.getArgument(0)
                        + "&scope=" + inv.getArgument(1)
                        + "&state=" + inv.getArgument(2) + "#wechat_redirect");
        WechatMpAuthChannel channel = new WechatMpAuthChannel(stubMpHolder(oauth2));
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        AuthorizeResult result = channel.authorize(baseConfig().setScope(null), null);
        assertThat(result.getAuthorizeUrl()).contains("open.weixin.qq.com/connect/oauth2/authorize");
        assertThat(result.getAuthorizeUrl()).contains("scope=snsapi_userinfo");
    }

    @Test
    void wechat_mini_前端取票据并归一openId() throws Exception {
        // jsCode2SessionInfo 由 WxMaService 提供，用 mock 的 ma holder
        cn.binarywang.wx.miniapp.api.WxMaService ma =
                mock(cn.binarywang.wx.miniapp.api.WxMaService.class);
        cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult session =
                new cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult();
        session.setOpenid("mini-open-001");
        session.setUnionid("mini-union-001");
        session.setSessionKey("sk-xxx");
        when(ma.jsCode2SessionInfo("js-code-1")).thenReturn(session);
        WxClientHolder holder = mock(WxClientHolder.class);
        when(holder.ma(any(AuthConfig.class))).thenReturn(ma);

        WechatMiniAuthChannel channel = new WechatMiniAuthChannel(holder);
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("js_code", "js-code-1");
        AuthResult<AuthUser> result = channel.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getChannelCode()).isEqualTo("wechat_mini");
        assertThat(result.getData().getOpenId()).isEqualTo("mini-open-001");
        assertThat(result.getData().getUnionId()).isEqualTo("mini-union-001");
        assertThat(result.getData().getRawUserInfo()).containsKey("session_key");
    }

    @Test
    void 企微扫码_授权URL是qrConnect端点() {
        WecomQrAuthChannel channel = new WecomQrAuthChannel(new WxClientHolder());
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        AuthConfig config = baseConfig().setAttr(wecomAttr());

        AuthorizeResult result = channel.authorize(config, null);
        assertThat(result.getAuthorizeUrl()).contains("open.work.weixin.qq.com/wwopen/sso/qrConnect");
        assertThat(result.getAuthorizeUrl()).contains("appid=wx123");
        assertThat(result.getAuthorizeUrl()).contains("agentid=1000002");
    }

    @Test
    void 企微新版Web登录_授权URL是wwlogin端点() {
        WecomWebV2AuthChannel channel = new WecomWebV2AuthChannel(new WxClientHolder());
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        AuthConfig config = baseConfig().setAttr(wecomAttr());

        AuthorizeResult result = channel.authorize(config, null);
        assertThat(result.getAuthorizeUrl()).contains("login.work.weixin.qq.com/wwlogin/sso/login");
        assertThat(result.getAuthorizeUrl()).contains("login_type=CorpApp");
        assertThat(result.getAuthorizeUrl()).contains("agentid=1000002");
    }

    @Test
    void wechat_wecom_web_授权URL走cp的oauth2authorize且默认静默scope() {
        WxCpOAuth2Service oauth2 = mock(WxCpOAuth2Service.class);
        when(oauth2.buildAuthorizationUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx123"
                        + "&redirect_uri=" + inv.getArgument(0)
                        + "&scope=" + inv.getArgument(2)
                        + "&state=" + inv.getArgument(1));
        WecomWebAuthChannel channel = new WecomWebAuthChannel(stubCpHolder(oauth2));
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        AuthorizeResult result = channel.authorize(baseConfig().setScope(null), null);
        assertThat(result.getAuthorizeUrl()).contains("open.weixin.qq.com/connect/oauth2/authorize");
        assertThat(result.getAuthorizeUrl()).contains("scope=snsapi_base");
    }

    @Test
    void wechat_wecom_web_code换userid并归一AuthUser() throws Exception {
        WxCpOauth2UserInfo info = new WxCpOauth2UserInfo();
        info.setUserId("wecom-user-001");
        info.setOpenId("wecom-open-001");

        WxCpOAuth2Service oauth2 = mock(WxCpOAuth2Service.class);
        when(oauth2.getUserInfo(eq(1000002), eq("the-code"))).thenReturn(info);

        WecomWebAuthChannel noState = new WecomWebAuthChannel(stubCpHolder(oauth2)) {
            @Override
            public boolean requireStateCheck() { return false; }
        };
        noState.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "the-code");

        AuthResult<AuthUser> result = noState.exchange(baseConfig().setAttr(wecomAttr()),
                new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getChannelCode()).isEqualTo("wechat_wecom_web");
        assertThat(result.getData().getOpenId()).isEqualTo("wecom-open-001");
        assertThat(result.getData().getUsername()).isEqualTo("wecom-user-001");
        assertThat(result.getData().getRawUserInfo()).containsKey("userId");
    }

    @Test
    void wechat_wecom_third_授权URL是qrConnect且携agentid() {
        WecomThirdAuthChannel channel = new WecomThirdAuthChannel(new WxClientHolder());
        channel.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());

        AuthorizeResult result = channel.authorize(baseConfig().setAttr(wecomAttr()), null);
        assertThat(result.getAuthorizeUrl()).contains("open.work.weixin.qq.com/wwopen/sso/qrConnect");
        assertThat(result.getAuthorizeUrl()).contains("appid=wx123");
        assertThat(result.getAuthorizeUrl()).contains("agentid=1000002");
    }

    @Test
    void wechat_wecom_third_getAuthUserInfo归一externalUserId() throws Exception {
        WxCpOauth2UserInfo info = new WxCpOauth2UserInfo();
        info.setUserId("third-user-001");
        info.setExternalUserId("external-001");

        WxCpOAuth2Service oauth2 = mock(WxCpOAuth2Service.class);
        when(oauth2.getAuthUserInfo("the-code")).thenReturn(info);

        WecomThirdAuthChannel noState = new WecomThirdAuthChannel(stubCpHolder(oauth2)) {
            @Override
            public boolean requireStateCheck() { return false; }
        };
        noState.setStateCache(new com.sfi.conflux.auth.support.InMemoryStateCache());
        Map<String, String> params = new HashMap<String, String>();
        params.put("code", "the-code");

        AuthResult<AuthUser> result = noState.exchange(baseConfig(), new AuthCallbackRequest(params));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getChannelCode()).isEqualTo("wechat_wecom_third");
        assertThat(result.getData().getOpenId()).isEqualTo("third-user-001");
        assertThat(result.getData().getRawUserInfo()).containsKey("externalUserId");
    }

    @Test
    void spi_自动注册微信全家桶7种形态() {
        AuthConfigProvider provider = mock(AuthConfigProvider.class);
        AuthGateway gateway = AuthGateway.builder().configProvider(provider).build();
        assertThat(gateway.registeredChannels()).contains(
                "wechat_open", "wechat_mp", "wechat_mini",
                "wechat_wecom_web", "wechat_wecom_qr", "wechat_wecom_web_v2", "wechat_wecom_third");
    }

    private static AuthConfig baseConfig() {
        return new AuthConfig()
                .setClientId("wx123")
                .setClientSecret("secret")
                .setRedirectUri("https://a.com/cb");
    }

    private static Map<String, String> wecomAttr() {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("agentId", "1000002");
        return attr;
    }

    private static WxClientHolder stubCpHolder(WxCpOAuth2Service oauth2) {
        WxCpService cp = mock(WxCpService.class);
        when(cp.getOauth2Service()).thenReturn(oauth2);
        WxClientHolder holder = mock(WxClientHolder.class);
        when(holder.cp(any(AuthConfig.class))).thenReturn(cp);
        return holder;
    }

    private static WxClientHolder stubMpHolder(WxOAuth2Service oauth2) {
        WxMpService mp = mock(WxMpService.class);
        when(mp.getOAuth2Service()).thenReturn(oauth2);
        WxClientHolder holder = mock(WxClientHolder.class);
        when(holder.mp(any(AuthConfig.class))).thenReturn(mp);
        when(holder.service(any(AuthConfig.class))).thenReturn(mp);
        return holder;
    }
}
