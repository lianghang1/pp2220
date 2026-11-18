package io.github.lianghang1.conflux.pay.autoconfigure;

import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.channel.CallbackConfigLookup;
import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channels.mock.MockChannelProvider;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多账户配置反查测试：验证普通商户的存量兼容、服务商模式的账户隔离、
 * 账户键冲突的启动期失败，以及回调事件归属到正确的 appId。
 *
 * @author lianghang
 */
class PropertiesChannelConfigProviderTest {

    /** 每个 spec = {appId, channelAppId(mock appKey), subMchNo}。 */
    private static PropertiesChannelConfigProvider provider(String[][] specs) {
        PayGatewayProperties properties = new PayGatewayProperties();
        for (String[] spec : specs) {
            PayGatewayProperties.ChannelProperties channel = new PayGatewayProperties.ChannelProperties();
            Map<String, String> attrs = new HashMap<String, String>();
            attrs.put("appKey", spec[1]);
            channel.setAttrs(attrs);
            if (spec[2] != null) {
                channel.setSubMchNo(spec[2]);
            }
            Map<String, PayGatewayProperties.ChannelProperties> channels =
                    new HashMap<String, PayGatewayProperties.ChannelProperties>();
            channels.put("mock", channel);
            properties.getApps().put(spec[0], channels);
        }
        return new PropertiesChannelConfigProvider(properties,
                Collections.<ChannelProvider>singletonList(new MockChannelProvider()));
    }

    @Test
    void ordinaryMerchantKeepsLegacyReverseLookup() {
        PropertiesChannelConfigProvider p = provider(new String[][]{{"app-a", "key-a", null}});

        ChannelConfig config = p.getByAccountKey("mock", "key-a");
        assertThat(config.getAppId()).isEqualTo("app-a");
        assertThat(config.getAccountKey()).isEqualTo("key-a");
        assertThat(p.getConfig("app-a", "mock")).isSameAs(config);
        assertThat(p.getByAccountKey("mock", "key-a")).isSameAs(config);
        assertThat(ChannelAccountKey.isComposite(config.getAccountKey())).isFalse();
    }

    @Test
    void serviceProviderAccountsWithSameChannelAppIdAreDistinguishable() {
        PropertiesChannelConfigProvider p = provider(new String[][]{
                {"app-a", "wx-shared", "sub-1"},
                {"app-b", "wx-shared", "sub-2"}});

        ChannelConfig a = p.getByAccountKey("mock", "wx-shared#sub-1");
        ChannelConfig b = p.getByAccountKey("mock", "wx-shared#sub-2");
        assertThat(a.getAppId()).isEqualTo("app-a");
        assertThat(a.getSubMchNo()).isEqualTo("sub-1");
        assertThat(b.getAppId()).isEqualTo("app-b");
        assertThat(b.getSubMchNo()).isEqualTo("sub-2");
        assertThat(a).isNotSameAs(b);
        assertThat(ChannelAccountKey.isComposite(a.getAccountKey())).isTrue();
    }

    @Test
    void duplicateAccountKeyFailsFastInsteadOfSilentOverride() {
        assertThatThrownBy(() -> provider(new String[][]{
                {"app-a", "wx-shared", null},
                {"app-b", "wx-shared", null}}))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("渠道账户标识冲突")
                .hasMessageContaining("wx-shared")
                .hasMessageContaining("app-a")
                .hasMessageContaining("app-b")
                .satisfies(thrown -> assertThat(((GatewayException) thrown).getErrorCode())
                        .isEqualTo(GatewayErrorCode.CONFIG_CONFLICT));
    }

    @Test
    void callbackRoutingPrefersAccountKeyAndFallsBackToLegacy() {
        Map<String, String> query = new HashMap<String, String>();
        query.put("accountKey", "wx-shared#sub-2");
        query.put("appId", "wx-shared");
        ChannelCallbackRequest routed = new ChannelCallbackRequest().setQueryParams(query);
        assertThat(ChannelAccountKey.resolve(routed, "wx-shared")).isEqualTo("wx-shared#sub-2");

        ChannelCallbackRequest legacy = new ChannelCallbackRequest()
                .setQueryParams(Collections.singletonMap("appId", "wx-legacy"));
        assertThat(ChannelAccountKey.resolve(legacy, "wx-legacy")).isEqualTo("wx-legacy");
        assertThat(ChannelAccountKey.resolve(new ChannelCallbackRequest(), "wx-legacy"))
                .isEqualTo("wx-legacy");
    }

    @Test
    void accountKeyGenerationRules() {
        assertThat(ChannelAccountKey.of("wx-a", null)).isEqualTo("wx-a");
        assertThat(ChannelAccountKey.of("wx-a", "  ")).isEqualTo("wx-a");
        assertThat(ChannelAccountKey.of("wx-a", " sub-1 ")).isEqualTo("wx-a#sub-1");
        assertThat(ChannelAccountKey.of(null, "sub-1")).isNull();
    }

    @Test
    void callbackEventIsAttributedToTheCorrectAppId() {
        PropertiesChannelConfigProvider p = provider(new String[][]{
                {"app-a", "shared-key", "sub-1"},
                {"app-b", "shared-key", "sub-2"}});
        CallbackConfigLookup lookup = new CallbackConfigLookup() {
            @Override
            public ChannelConfig byAccountKey(String accountKey) {
                return p.getByAccountKey("mock", accountKey);
            }
        };
        ChannelCallbackHandler handler = new MockChannelProvider().callbackHandler();
        Map<String, String> headers = Collections.singletonMap(
                MockChannelProvider.SIGN_HEADER, "shared-key");

        ChannelCallbackRequest forAppB = new ChannelCallbackRequest()
                .setHeaders(headers)
                .setQueryParams(Collections.singletonMap("accountKey", "shared-key#sub-2"))
                .setBody("{\"type\":\"PAYMENT\",\"bizOrderNo\":\"B1\",\"success\":true}");
        assertThat(handler.parseCallback(forAppB, lookup).getAppId()).isEqualTo("app-b");

        ChannelCallbackRequest forAppA = new ChannelCallbackRequest()
                .setHeaders(headers)
                .setQueryParams(Collections.singletonMap("accountKey", "shared-key#sub-1"))
                .setBody("{\"type\":\"PAYMENT\",\"bizOrderNo\":\"B2\",\"success\":true}");
        assertThat(handler.parseCallback(forAppA, lookup).getAppId()).isEqualTo("app-a");
    }
}
