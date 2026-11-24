package com.sfi.conflux.pay.channels.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.platform.alipay.AlipayClients;
import com.sfi.conflux.platform.core.PlatformCredential;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝 SDK 客户端持有器（支付域适配层）：把 {@link ChannelConfig} 映射为平台统一凭证，
 * 取 client 的动作委托平台层 {@link AlipayClients}。
 *
 * <p>官方 alipay-sdk-java 的签名脏活（RSA2、公钥/证书模式）由 SDK 负责，client 的构造与缓存下沉到
 * 平台层门面；本类只做「ChannelConfig → PlatformCredential」字段映射：appId→appId、privateKey→secret，
 * alipayPublicKey/gateway/effectiveGateway(沙箱)/signType 透传 attrs。保留原方法签名，渠道实现零改动。</p>
 *
 * <p>沙箱开关由 {@link ChannelConfig#isSandbox()} 承载，映射到 {@code attrs.gateway} 时取
 * {@link AlipayParams#effectiveGateway(boolean)} 的结算结果，确保沙箱环境不会误走生产网关。</p>
 *
 * <p>调用职责仍留在域层：{@link #execute} 的 subCode 业务失败包装、ISV {@code appAuthToken} 透传——
 * 平台层只返回原生 client，不接管这些语义。</p>
 *
 * @author lianghang
 */
public class AlipayClientHolder {

    private final AlipayClients clients = new AlipayClients();

    /** 取指定配置对应的客户端（平台层按凭证指纹构造并缓存）。 */
    public AlipayClient client(ChannelConfig config) {
        return clients.client(credential(config));
    }

    /** ChannelConfig → 平台统一凭证：appId/私钥为主字段，其余平台参数透传 attrs（含沙箱生效网关）。 */
    private static PlatformCredential credential(ChannelConfig config) {
        AlipayParams params = paramsOf(config);
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        attrs.put("alipayPublicKey", params.getAlipayPublicKey());
        // 沙箱开关：显式 gateway 优先，否则按 sandbox 切到沙箱网关，结果作为统一 gateway 交给平台层
        attrs.put("gateway", params.effectiveGateway(config.isSandbox()));
        if (!StringUtils.isBlank(params.getSignType())) {
            attrs.put("signType", params.getSignType());
        }
        return PlatformCredential.of(params.getAppId(), params.getPrivateKey(), attrs);
    }

    public static AlipayParams paramsOf(ChannelConfig config) {
        if (config instanceof AlipayParams) {
            return (AlipayParams) config;
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "支付宝渠道参数类型不匹配: {}", config == null ? "null" : config.getClass().getName());
    }

    /** 统一接口调用包装：SDK 异常与业务失败（subCode 非空）收敛为网关渠道异常；ISV 授权令牌非空时自动携带。 */
    public <T extends AlipayResponse> T execute(String action, ChannelConfig config, RequestCaller<T> caller) {
        T response = executeRaw(action, config, caller);
        if (response != null && !response.isSuccess()
                && StringUtils.isNotBlank(response.getSubCode())) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "支付宝{}失败: subCode={}, subMsg={}", action, response.getSubCode(), response.getSubMsg());
        }
        return response;
    }

    /**
     * 原始调用包装：仅收敛 SDK/系统异常，不把业务 subCode 失败转成异常。
     *
     * <p>供"查单/退款查单"这类需要读取 subCode 归一为业务状态（如交易不存在=支付中、
     * 退款查无记录=处理中）的场景使用——若走 {@link #execute}，业务失败会先被抛成异常，
     * 导致这些特判分支永不可达。</p>
     */
    public <T extends AlipayResponse> T executeRaw(String action, ChannelConfig config, RequestCaller<T> caller) {
        try {
            return caller.call(client(config), authTokenOf(config));
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
