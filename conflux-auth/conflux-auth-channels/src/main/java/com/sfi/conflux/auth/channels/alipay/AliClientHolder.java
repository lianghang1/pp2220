package com.sfi.conflux.auth.channels.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.sfi.conflux.auth.config.AuthConfig;
import com.sfi.conflux.platform.alipay.AlipayClients;
import com.sfi.conflux.platform.core.PlatformCredential;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝登录客户端持有器（登录域适配层）：把 {@link AuthConfig} 映射为统一凭证，
 * 取 client 的动作委托平台层 {@link AlipayClients}。
 *
 * <p>官方 alipay-sdk-java 的签名脏活（RSA2、公钥/证书模式、同步响应验签）全部由 SDK 负责；
 * 本类只做「AuthConfig → PlatformCredential」字段映射：clientId→appId、clientSecret（私钥）→secret，
 * gateway/signMode/证书路径/alipayPublicKey/signType 原样透传 attrs。保留原方法签名，渠道实现与既有测试零改动。</p>
 *
 * @author lianghang
 */
public class AliClientHolder {

    private final AlipayClients clients = new AlipayClients();

    /** 取客户端：公钥模式 execute()，证书模式内部已切 certificateExecute 所需的 client */
    public AlipayClient client(AuthConfig config) throws AlipayApiException {
        return clients.client(credential(config));
    }

    /** AuthConfig → 平台统一凭证：clientId→appId、clientSecret→secret、其余平台参数透传 attrs */
    private static PlatformCredential credential(AuthConfig config) {
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        Map<String, String> source = config.getAttr();
        if (source != null) {
            attrs.putAll(source);
        }
        return PlatformCredential.of(config.getClientId(), config.getClientSecret(), attrs);
    }
}
