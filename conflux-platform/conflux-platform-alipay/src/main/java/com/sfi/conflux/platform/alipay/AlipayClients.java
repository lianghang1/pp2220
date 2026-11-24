package com.sfi.conflux.platform.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.sfi.conflux.platform.core.ClientCache;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 支付宝平台客户端薄门面：以统一凭证换取支付宝官方原生 {@link AlipayClient}。
 *
 * <p>支付宝登录 / 通知 / 支付共用同一个 {@code DefaultAlipayClient} 类型（网关 {@code gateway.do + method}
 * 协议），不像微信按形态拆多个 service——因此门面只有 {@link #client(PlatformCredential)} 一个方法。
 * 公钥模式与证书模式由凭证 {@code attrs.signMode} 驱动，构造参数全部来自 attrs（网关、公钥、证书路径、签名类型）。</p>
 *
 * <p><b>不封任何能力</b>：门面只返回原生 client，调用方拿到后直接 {@code client.execute(XxxRequest)}
 * 或 {@code certificateExecute(...)} 即可使用官方 SDK 全部接口（转账/账单/消息/小程序能力等），
 * 平台层不包装 client、不转发方法。</p>
 *
 * @author lianghang
 */
public final class AlipayClients {

    /** 默认生产网关（与 auth 域 AliSupport 一致，可用 attrs.gateway 覆盖） */
    private static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";

    private final ClientCache cache = new ClientCache();

    /**
     * 取原生 AlipayClient：公钥模式 / 证书模式由凭证 attrs 决定。
     *
     * @param c 统一凭证（appId=应用ID、secret=应用私钥，attrs 含网关/公钥/证书路径/签名类型）
     * @return 支付宝官方原生 client，调用方直接调 SDK 接口
     */
    public AlipayClient client(PlatformCredential c) {
        String fingerprint = c.appId + "|" + c.secret + "|" + c.attrs;
        return cache.get("alipay:client", fingerprint, () -> build(c));
    }

    /** 按签名模式构造原生 client（构造逻辑复刻 auth 域 AliClientHolder，改为从 attrs 读取） */
    private static AlipayClient build(PlatformCredential c) throws Exception {
        String gateway = gateway(c);
        if (certificateMode(c)) {
            CertAlipayRequest cert = new CertAlipayRequest();
            cert.setServerUrl(gateway);
            cert.setAppId(c.appId);
            cert.setPrivateKey(c.secret);
            cert.setFormat("json");
            cert.setCharset("UTF-8");
            cert.setSignType(signType(c));
            cert.setCertPath(attr(c, "appCertPath"));
            cert.setAlipayPublicCertPath(attr(c, "alipayCertPath"));
            cert.setRootCertPath(attr(c, "rootCertPath"));
            return new DefaultAlipayClient(cert);
        }
        return new DefaultAlipayClient(
                gateway, c.appId, c.secret, "json", "UTF-8",
                attr(c, "alipayPublicKey"), signType(c));
    }

    /** 网关：attrs.gateway 覆盖，默认生产 */
    private static String gateway(PlatformCredential c) {
        String gw = attr(c, "gateway");
        return (gw == null || gw.trim().isEmpty()) ? DEFAULT_GATEWAY : gw.trim();
    }

    /** 签名类型：attrs.signType 覆盖，默认 RSA2 */
    private static String signType(PlatformCredential c) {
        String st = attr(c, "signType");
        return (st == null || st.trim().isEmpty()) ? "RSA2" : st.trim();
    }

    /** 证书模式：attrs.signMode=cert / certificate（否则公钥模式） */
    private static boolean certificateMode(PlatformCredential c) {
        String mode = attr(c, "signMode");
        return "cert".equalsIgnoreCase(mode) || "certificate".equalsIgnoreCase(mode);
    }

    private static String attr(PlatformCredential c, String key) {
        return c.attrs.get(key);
    }
}
