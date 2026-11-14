package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付宝 SDK 客户端持有器：按应用凭证缓存 DefaultAlipayClient，统一异常包装。
 *
 * <p>AlipayClient 实例线程安全可复用；支付宝接口失败（业务错误码在响应体中）
 * 经 {@link #wrap} 转换为网关渠道异常并保留支付宝原始错误码。</p>
 *
 * @author lianghang
 */
public class AlipayClientHolder {

    private final ConcurrentHashMap<AlipayParams, AlipayClient> clients =
            new ConcurrentHashMap<AlipayParams, AlipayClient>();

    /** 取指定配置对应的客户端（按需构建并缓存）。 */
    public AlipayClient client(ChannelConfig config) {
        AlipayParams params = paramsOf(config);
        return clients.computeIfAbsent(params, key -> {
            try {
                AlipayConfig alipayConfig = new AlipayConfig();
                alipayConfig.setServerUrl(key.effectiveGateway());
                alipayConfig.setAppId(key.getAppId());
                alipayConfig.setPrivateKey(key.getPrivateKey());
                alipayConfig.setAlipayPublicKey(key.getAlipayPublicKey());
                alipayConfig.setSignType(key.getSignType());
                alipayConfig.setFormat("json");
                alipayConfig.setCharset("UTF-8");
                return new DefaultAlipayClient(alipayConfig);
            } catch (AlipayApiException e) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                        "支付宝客户端构建失败: {}", e.getMessage());
            }
        });
    }

    public static AlipayParams paramsOf(ChannelConfig config) {
        if (config.getParams() instanceof AlipayParams) {
            return (AlipayParams) config.getParams();
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "支付宝渠道参数类型不匹配: {}", config.getParams() == null ? "null" : config.getParams().getClass().getName());
    }

    /** 统一接口调用包装：SDK 异常与业务失败（subCode 非空）收敛为网关渠道异常；ISV 授权令牌非空时自动携带。 */
    public <T extends AlipayResponse> T execute(String action, ChannelConfig config, RequestCaller<T> caller) {
        try {
            T response = caller.call(client(config), authTokenOf(config));
            if (response != null && !response.isSuccess()
                    && io.github.lianghang1.paygateway.util.StringUtils.isNotBlank(response.getSubCode())) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "支付宝{}失败: subCode={}, subMsg={}", action, response.getSubCode(), response.getSubMsg());
            }
            return response;
        } catch (GatewayException e) {
            throw e;
        } catch (AlipayApiException e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝{}失败: {}", action, e.getMessage(), e);
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝{}失败: {}", action, e.getMessage(), e);
        }
    }

    /** 渠道配置的 ISV 授权令牌（普通商户返回 null）。 */
    private String authTokenOf(ChannelConfig config) {
        return paramsOf(config).getAppAuthToken();
    }

    /** 接口调用函数签名：authToken 为 ISV 代商户授权令牌（普通商户为 null，
     * 实现方据此决定调用 {@code execute(request)} 或 {@code execute(request, authToken)}）。 */
    public interface RequestCaller<T> {
        T call(AlipayClient client, String authToken) throws AlipayApiException;
    }
}
