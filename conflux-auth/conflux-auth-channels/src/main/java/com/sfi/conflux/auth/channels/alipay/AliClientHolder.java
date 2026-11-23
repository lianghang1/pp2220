package com.sfi.conflux.auth.channels.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.sfi.conflux.auth.config.AuthConfig;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付宝官方 SDK 客户端持有器：按应用标识缓存 {@link AlipayClient}。
 *
 * <p>签名脏活（RSA2、公钥/证书模式、同步响应验签）全部交给官方 alipay-sdk-java，本门面不自研签名。
 * 两种模式由 {@code attr.signMode} 选择：</p>
 * <ul>
 *   <li><b>公钥模式（默认）</b>：{@code attr.alipayPublicKey} 为支付宝公钥，应用私钥取 clientSecret；</li>
 *   <li><b>证书模式</b>：{@code attr.appCertPath / alipayCertPath / rootCertPath} 三个证书路径，
 *       用 {@link CertAlipayRequest} 构造，调用走 {@code certificateExecute}。</li>
 * </ul>
 *
 * <p>与微信 WxClientHolder 一致：零 Redis、纯内存缓存、secret 变更自动重建。</p>
 *
 * @author lianghang
 */
public class AliClientHolder {

    private final ConcurrentHashMap<String, AlipayClient> clients = new ConcurrentHashMap<String, AlipayClient>();
    private final ConcurrentHashMap<String, String> secrets = new ConcurrentHashMap<String, String>();

    /** 取客户端：公钥模式 execute()，证书模式内部已切 certificateExecute 所需的 client */
    public AlipayClient client(AuthConfig config) throws AlipayApiException {
        String key = "ali:" + config.getClientId() + ":" + AliSupport.certificateMode(config);
        String secretFingerprint = fingerprint(config);
        String cachedSecret = secrets.get(key);
        if (secretFingerprint.equals(cachedSecret)) {
            AlipayClient hit = clients.get(key);
            if (hit != null) {
                return hit;
            }
        }
        AlipayClient built = build(config);
        clients.put(key, built);
        secrets.put(key, secretFingerprint);
        return built;
    }

    private AlipayClient build(AuthConfig config) throws AlipayApiException {
        String gateway = AliSupport.gateway(config);
        String appId = config.getClientId();
        String privateKey = config.getClientSecret();
        if (AliSupport.certificateMode(config)) {
            CertAlipayRequest cert = new CertAlipayRequest();
            cert.setServerUrl(gateway);
            cert.setAppId(appId);
            cert.setPrivateKey(privateKey);
            cert.setFormat("json");
            cert.setCharset("UTF-8");
            cert.setSignType("RSA2");
            cert.setCertPath(AliSupport.param(config, null, "appCertPath"));
            cert.setAlipayPublicCertPath(AliSupport.param(config, null, "alipayCertPath"));
            cert.setRootCertPath(AliSupport.param(config, null, "rootCertPath"));
            return new DefaultAlipayClient(cert);
        }
        String alipayPublicKey = AliSupport.param(config, null, "alipayPublicKey");
        return new DefaultAlipayClient(gateway, appId, privateKey, "json", "UTF-8", alipayPublicKey, "RSA2");
    }

    /** 用私钥 + 模式相关参数做指纹，任一变更即重建 client */
    private String fingerprint(AuthConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getClientSecret());
        if (AliSupport.certificateMode(config)) {
            sb.append('|').append(AliSupport.param(config, null, "appCertPath"))
              .append('|').append(AliSupport.param(config, null, "alipayCertPath"))
              .append('|').append(AliSupport.param(config, null, "rootCertPath"));
        } else {
            sb.append('|').append(AliSupport.param(config, null, "alipayPublicKey"));
        }
        sb.append('|').append(AliSupport.gateway(config));
        return sb.toString();
    }
}
