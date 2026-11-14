package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.billdownload.BillDownloadService;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.profitsharing.ProfitsharingService;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.transferbatch.TransferBatchService;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付 SDK 客户端持有器：按商户凭证缓存平台证书配置与各 API 服务实例。
 *
 * <p>平台证书由 RSAAutoCertificateConfig 自动下载与轮换；同一商户凭证
 * （WechatParams equals 语义）全局复用同一份 Config，避免重复下载。
 * 所有对外方法线程安全；SDK 服务实例自身线程安全，可并发调用。</p>
 *
 * @author lianghang
 */
public class WechatClientHolder {

    private final ConcurrentHashMap<WechatParams, ClientSet> clients = new ConcurrentHashMap<WechatParams, ClientSet>();

    /** 取指定配置对应的客户端集合（按需构建并缓存）。 */
    public ClientSet clients(ChannelConfig config) {
        WechatParams params = paramsOf(config);
        return clients.computeIfAbsent(params, key -> new ClientSet(key));
    }

    /** 回调验签解析器（以该配置的平台证书集合验签）。 */
    public NotificationParser notificationParser(ChannelConfig config) {
        return clients(config).notificationParser;
    }

    public static WechatParams paramsOf(ChannelConfig config) {
        if (config.getParams() instanceof WechatParams) {
            return (WechatParams) config.getParams();
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "微信渠道参数类型不匹配: {}", config.getParams() == null ? "null" : config.getParams().getClass().getName());
    }

    /** 商户私钥 PEM 文本解析（容忍头尾修饰与空白）。 */
    public static PrivateKey readPrivateKey(String pem) {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] encoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING, "商户私钥解析失败: {}", e.getMessage());
        }
    }

    /** 某商户凭证对应的全部 SDK 服务实例。 */
    public static class ClientSet {

        private final RSAAutoCertificateConfig rsaConfig;
        private final NotificationParser notificationParser;
        private final JsapiServiceExtension jsapiService;
        private final NativePayService nativePayService;
        private final H5Service h5Service;
        private final AppServiceExtension appService;
        private final RefundService refundService;
        private final TransferBatchService transferBatchService;
        private final ProfitsharingService profitSharingService;
        private final BillDownloadService billDownloadService;

        private ClientSet(WechatParams params) {
            this.rsaConfig = new RSAAutoCertificateConfig.Builder()
                    .merchantId(params.getMerchantId())
                    .privateKey(params.getMerchantPrivateKey())
                    .merchantSerialNumber(params.getMerchantSerialNumber())
                    .apiV3Key(params.getApiV3Key())
                    .build();
            this.notificationParser = new NotificationParser(rsaConfig);
            this.jsapiService = new JsapiServiceExtension.Builder().config(rsaConfig).build();
            this.nativePayService = new NativePayService.Builder().config(rsaConfig).build();
            this.h5Service = new H5Service.Builder().config(rsaConfig).build();
            this.appService = new AppServiceExtension.Builder().config(rsaConfig).build();
            this.refundService = new RefundService.Builder().config(rsaConfig).build();
            this.transferBatchService = new TransferBatchService.Builder().config(rsaConfig).build();
            this.profitSharingService = new ProfitsharingService.Builder().config(rsaConfig).build();
            this.billDownloadService = new BillDownloadService.Builder().config(rsaConfig).build();
        }

        public RSAAutoCertificateConfig rsaConfig() {
            return rsaConfig;
        }

        public JsapiServiceExtension jsapiService() {
            return jsapiService;
        }

        public NativePayService nativePayService() {
            return nativePayService;
        }

        public H5Service h5Service() {
            return h5Service;
        }

        public AppServiceExtension appService() {
            return appService;
        }

        public RefundService refundService() {
            return refundService;
        }

        public TransferBatchService transferBatchService() {
            return transferBatchService;
        }

        public ProfitsharingService profitSharingService() {
            return profitSharingService;
        }

        public BillDownloadService billDownloadService() {
            return billDownloadService;
        }
    }
}
