package com.sfi.conflux.pay.autoconfigure;

import com.sfi.conflux.pay.api.ChannelCallbackRequest;
import com.sfi.conflux.pay.channel.ChannelCallbackHandler;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelProvider;
import com.sfi.conflux.pay.channels.mock.MockChannelProvider;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置文件装载测试：验证 configs.<configId>.<channelCode> 正向装载，
 * 以及回调事件由业务传入 configId 后归属到正确配置（无隐式反查）。
 *
 * @author lianghang
 */
class PropertiesChannelConfigProviderTest {

    /** 每个 spec = {configId, channelAppId(mock appKey)}。 */
    private static PropertiesChannelConfigProvider provider(String[][] specs) {
        PayGatewayProperties properties = new PayGatewayProperties();
        for (String[] spec : specs) {
            PayGatewayProperties.ChannelProperties channel = new PayGatewayProperties.ChannelProperties();
            Map<String, String> attr = new HashMap<String, String>();
            attr.put("appKey", spec[1]);
            channel.setAttr(attr);
            Map<String, PayGatewayProperties.ChannelProperties> channels =
                    new HashMap<String, PayGatewayProperties.ChannelProperties>();
            channels.put("mock", channel);
            properties.getConfigs().put(spec[0], channels);
        }
        return new PropertiesChannelConfigProvider(properties,
                Collections.<ChannelProvider>singletonList(new MockChannelProvider()));
    }

    @Test
    void configIsLoadedByConfigIdAndChannel() {
        PropertiesChannelConfigProvider p = provider(new String[][]{{"cfg-a", "key-a"}});

        ChannelConfig config = p.getConfig("cfg-a", "mock");
        assertThat(config.getConfigId()).isEqualTo("cfg-a");
        assertThat(config.getChannelCode()).isEqualTo("mock");
        assertThat(p.getConfig("cfg-a", "mock")).isSameAs(config);

        assertThatThrownBy(() -> p.getConfig("missing", "mock"))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(GatewayErrorCode.CONFIG_MISSING);
    }

    @Test
    void multipleConfigsAreIndependent() {
        PropertiesChannelConfigProvider p = provider(new String[][]{
                {"cfg-a", "key-a"},
                {"cfg-b", "key-b"}});

        ChannelConfig a = p.getConfig("cfg-a", "mock");
        ChannelConfig b = p.getConfig("cfg-b", "mock");
        assertThat(a).isNotSameAs(b);
        assertThat(a.getConfigId()).isEqualTo("cfg-a");
        assertThat(b.getConfigId()).isEqualTo("cfg-b");
    }

    @Test
    void callbackEventCarriesBusinessProvidedConfigId() {
        PropertiesChannelConfigProvider p = provider(new String[][]{
                {"cfg-b", "shared-key"}});
        ChannelConfig config = p.getConfig("cfg-b", "mock");

        ChannelCallbackHandler handler = new MockChannelProvider().callbackHandler();
        Map<String, String> headers = Collections.singletonMap(
                MockChannelProvider.SIGN_HEADER, "shared-key");
        ChannelCallbackRequest request = new ChannelCallbackRequest()
                .setHeaders(headers)
                .setBody("{\"type\":\"PAYMENT\",\"bizOrderNo\":\"B1\",\"success\":true}");

        // 业务传入 configId，handler 直接用对应 config 验签，事件携带该 configId
        assertThat(handler.parseCallback(request, config).getConfigId()).isEqualTo("cfg-b");
    }
}
