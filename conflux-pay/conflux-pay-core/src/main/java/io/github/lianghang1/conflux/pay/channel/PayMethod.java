package io.github.lianghang1.conflux.pay.channel;

import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付/转账方式注册表：方式编码 → 默认渠道。
 *
 * <p>方式编码决定路由到哪个渠道（如 WECHAT_NATIVE → wechat）；
 * 同一方式可经请求上的 channelCode 显式覆盖路由。三方渠道可在启动时
 * 通过 {@link #register} 注册自定义方式，零改内核。</p>
 *
 * <p>注册表全进程共享，线程安全；内置方式在静态初始化注册，运行期只读。</p>
 *
 * @author lianghang
 */
public final class PayMethod {

    private static final Map<String, PayMethod> REGISTRY = new ConcurrentHashMap<String, PayMethod>();

    // ---- 微信支付 ----
    public static final PayMethod WECHAT_JSAPI = register("WECHAT_JSAPI", ChannelCode.WECHAT);
    public static final PayMethod WECHAT_LITE = register("WECHAT_LITE", ChannelCode.WECHAT);
    public static final PayMethod WECHAT_NATIVE = register("WECHAT_NATIVE", ChannelCode.WECHAT);
    public static final PayMethod WECHAT_H5 = register("WECHAT_H5", ChannelCode.WECHAT);
    public static final PayMethod WECHAT_APP = register("WECHAT_APP", ChannelCode.WECHAT);
    public static final PayMethod WECHAT_BAR = register("WECHAT_BAR", ChannelCode.WECHAT);
    /** 微信商家转账（V3）/ 企业付款到零钱（V2），由 wxApiVersion 路由 */
    public static final PayMethod WECHAT_TRANS = register("WECHAT_TRANS", ChannelCode.WECHAT);

    // ---- 支付宝 ----
    public static final PayMethod ALI_QR = register("ALI_QR", ChannelCode.ALIPAY);
    public static final PayMethod ALI_WAP = register("ALI_WAP", ChannelCode.ALIPAY);
    public static final PayMethod ALI_PAGE = register("ALI_PAGE", ChannelCode.ALIPAY);
    public static final PayMethod ALI_APP = register("ALI_APP", ChannelCode.ALIPAY);
    public static final PayMethod ALI_JSAPI = register("ALI_JSAPI", ChannelCode.ALIPAY);
    public static final PayMethod ALI_BAR = register("ALI_BAR", ChannelCode.ALIPAY);
    /** 支付宝单笔转账（到支付宝账户） */
    public static final PayMethod ALI_TRANSFER = register("ALI_TRANSFER", ChannelCode.ALIPAY);

    // ---- 云闪付 ----
    public static final PayMethod UNION_QR = register("UNION_QR", ChannelCode.UNIONPAY);
    public static final PayMethod UNION_BAR = register("UNION_BAR", ChannelCode.UNIONPAY);
    public static final PayMethod UNION_WX_JSAPI = register("UNION_WX_JSAPI", ChannelCode.UNIONPAY);
    public static final PayMethod UNION_WX_BAR = register("UNION_WX_BAR", ChannelCode.UNIONPAY);
    public static final PayMethod UNION_ALI_JSAPI = register("UNION_ALI_JSAPI", ChannelCode.UNIONPAY);
    public static final PayMethod UNION_ALI_BAR = register("UNION_ALI_BAR", ChannelCode.UNIONPAY);

    // ---- PayPal ----
    public static final PayMethod PAYPAL_SALE = register("PAYPAL_SALE", ChannelCode.PAYPAL);
    public static final PayMethod PAYPAL_PAYOUT = register("PAYPAL_PAYOUT", ChannelCode.PAYPAL);

    // ---- 银联商务天满（按 微信/支付宝/云闪付 × 场景 组合）----
    public static final PayMethod CHINAUMS_WX_APP = register("CHINAUMS_WX_APP", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_WX_H5 = register("CHINAUMS_WX_H5", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_WX_MINI = register("CHINAUMS_WX_MINI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_WX_JSAPI = register("CHINAUMS_WX_JSAPI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_ALI_APP = register("CHINAUMS_ALI_APP", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_ALI_H5 = register("CHINAUMS_ALI_H5", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_ALI_MINI = register("CHINAUMS_ALI_MINI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_ALI_JSAPI = register("CHINAUMS_ALI_JSAPI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_UAC_APP = register("CHINAUMS_UAC_APP", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_UAC_H5 = register("CHINAUMS_UAC_H5", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_UAC_MINI = register("CHINAUMS_UAC_MINI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_UAC_JSAPI = register("CHINAUMS_UAC_JSAPI", ChannelCode.CHINAUMS);
    public static final PayMethod CHINAUMS_QR = register("CHINAUMS_QR", ChannelCode.CHINAUMS);

    // ---- 模拟渠道 ----
    public static final PayMethod MOCK_PAY = register("MOCK_PAY", ChannelCode.MOCK);
    public static final PayMethod MOCK_TRANS = register("MOCK_TRANS", ChannelCode.MOCK);

    private final String code;
    private final String defaultChannel;

    private PayMethod(String code, String defaultChannel) {
        this.code = code;
        this.defaultChannel = defaultChannel;
    }

    /** 注册自定义方式；编码重复时抛参数异常。 */
    public static PayMethod register(String code, String defaultChannel) {
        PayMethod method = new PayMethod(code, defaultChannel);
        PayMethod existing = REGISTRY.putIfAbsent(code, method);
        if (existing != null) {
            throw new IllegalStateException("PayMethod 已注册: " + code);
        }
        return method;
    }

    /** 按编码解析方式；未注册时抛 {@link GatewayErrorCode#METHOD_NOT_SUPPORTED}。 */
    public static PayMethod of(String code) {
        PayMethod method = code == null ? null : REGISTRY.get(code);
        if (method == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "未注册的支付/转账方式: {}", code);
        }
        return method;
    }

    /** 方式编码。 */
    public String getCode() {
        return code;
    }

    /** 默认渠道编码（可被请求上的显式渠道覆盖）。 */
    public String getDefaultChannel() {
        return defaultChannel;
    }

    @Override
    public String toString() {
        return code;
    }
}
