package io.github.lianghang1.paygateway.channels.unionpay;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.PaymentHandler;
import io.github.lianghang1.paygateway.channel.PaymentInvocation;
import io.github.lianghang1.paygateway.channel.PaymentSnapshot;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.AliBarPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.AliJsapiPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.UnionPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.WxBarPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.WxJsapiPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.YsfBarPayMethod;
import io.github.lianghang1.paygateway.channels.unionpay.paymethod.YsfQrPayMethod;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.model.PaymentState;
import io.github.lianghang1.paygateway.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 云闪付支付处理器门面：按支付方式路由到独立的 PayMethod 实现
 * （银联二维码/银联付款码/转接微信 JSAPI/转接微信付款码/转接支付宝 JSAPI/转接支付宝付款码），
 * 查单与关单统一按商户订单号处理。
 *
 * @author lianghang
 */
public class UnionPayPaymentHandler implements PaymentHandler {

    private final UnionPayClient client = new UnionPayClient();
    private final Map<String, UnionPayMethod> payMethods = new HashMap<String, UnionPayMethod>();

    public UnionPayPaymentHandler() {
        register(new YsfQrPayMethod());
        register(new YsfBarPayMethod());
        register(new WxJsapiPayMethod());
        register(new WxBarPayMethod());
        register(new AliJsapiPayMethod());
        register(new AliBarPayMethod());
    }

    private void register(UnionPayMethod payMethod) {
        payMethods.put(payMethod.method(), payMethod);
    }

    /** 渠道已支持的支付方式集合（能力声明/调试用）。 */
    public Set<String> supportedMethods() {
        return payMethods.keySet();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        UnionPayMethod payMethod = payMethodOf(payment);
        return payMethod.createPayment(payment, config, params, client);
    }

    private UnionPayMethod payMethodOf(Payment payment) {
        UnionPayMethod payMethod = payMethods.get(payment.getMethod());
        if (payMethod == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "云闪付渠道不支持支付方式: {}", payment.getMethod());
        }
        return payMethod;
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        Map<String, Object> requestParams = new HashMap<String, Object>();
        requestParams.put("orderNo", payment.getPaymentId());
        requestParams.put("orderTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestParams.put("txnAmt", payment.getAmount());
        requestParams.put("currencyCode", "156");
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        Map<String, Object> result = client.execute("查单", "/gateway/api/pay/queryOrder",
                config, requestParams);
        String origRespCode = result.get("origRespCode") == null
                ? null : String.valueOf(result.get("origRespCode"));
        return new PaymentSnapshot()
                .setChannelOrderNo(result.get("transIndex") == null
                        ? null : String.valueOf(result.get("transIndex")))
                .setState(UnionPayTranslator.fromOrigRespCode(origRespCode))
                .setRawMsg(origRespCode);
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        Map<String, Object> requestParams = new HashMap<String, Object>();
        requestParams.put("orderNo", payment.getPaymentId());
        requestParams.put("orderTime", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestParams.put("txnAmt", payment.getAmount());
        requestParams.put("currencyCode", "156");
        String merchantId = StringUtils.isNotBlank(config.getSubMchNo())
                ? config.getSubMchNo() : params.getMerchantId();
        if (merchantId != null) {
            requestParams.put("merId", merchantId);
        }
        if (params.getSerProvId() != null) {
            requestParams.put("serProvId", params.getSerProvId());
        }
        client.execute("关单", "/gateway/api/pay/closeOrder", config, requestParams);
    }
}
