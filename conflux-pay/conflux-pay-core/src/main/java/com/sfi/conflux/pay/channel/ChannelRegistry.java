package com.sfi.conflux.pay.channel;

import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道注册表：渠道编码 → 渠道提供者，及其能力处理器的查找入口。
 *
 * <p>注册发生在网关组装阶段（启动期），运行期只读；查找缺失能力时抛出
 * 明确异常而非返回 null，避免调用方空指针。线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class ChannelRegistry {

    private final Map<String, ChannelProvider> providers = new ConcurrentHashMap<String, ChannelProvider>();

    /** 注册渠道提供者；渠道编码重复时后注册者覆盖并记录告警。 */
    public void register(ChannelProvider provider) {
        String code = provider.channelCode();
        ChannelProvider existing = providers.putIfAbsent(code, provider);
        if (existing != null) {
            log.warn("渠道提供者重复注册，保留先注册者: channel={}, keep={}, ignore={}",
                    code, existing.getClass().getName(), provider.getClass().getName());
            return;
        }
        log.info("渠道已注册: channel={}, methods={}, capabilities={}",
                code, provider.supportedMethods(), describeCapabilities(provider));
    }

    /** 按编码取渠道提供者；未注册时抛 {@link GatewayErrorCode#CHANNEL_NOT_FOUND}。 */
    public ChannelProvider provider(String channelCode) {
        ChannelProvider provider = channelCode == null ? null : providers.get(channelCode);
        if (provider == null) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_NOT_FOUND, "渠道未注册: {}", channelCode);
        }
        return provider;
    }

    /** 已注册渠道编码集合。 */
    public Set<String> registeredChannels() {
        return providers.keySet();
    }

    /** 渠道支付能力；渠道不支持时抛 {@link GatewayErrorCode#CHANNEL_CAPABILITY_NOT_SUPPORTED}。 */
    public PaymentHandler paymentHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).paymentHandler(), "支付");
    }

    /** 渠道退款能力。 */
    public RefundHandler refundHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).refundHandler(), "退款");
    }

    /** 渠道转账能力。 */
    public TransferHandler transferHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).transferHandler(), "转账");
    }

    /** 渠道分账能力。 */
    public ProfitShareHandler profitShareHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).profitShareHandler(), "分账");
    }

    /** 渠道用户标识能力。 */
    public ChannelUserHandler channelUserHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).channelUserHandler(), "用户标识");
    }

    /** 渠道账单能力。 */
    public BillHandler billHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).billHandler(), "账单");
    }

    /** 渠道回调解析能力。 */
    public ChannelCallbackHandler callbackHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).callbackHandler(), "回调");
    }

    private <T> T capability(String channelCode, T handler, String capabilityName) {
        if (handler == null) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "渠道[{}]不支持{}能力", channelCode, capabilityName);
        }
        return handler;
    }

    private String describeCapabilities(ChannelProvider provider) {
        StringBuilder sb = new StringBuilder("[");
        if (provider.paymentHandler() != null) {
            sb.append("payment,");
        }
        if (provider.refundHandler() != null) {
            sb.append("refund,");
        }
        if (provider.transferHandler() != null) {
            sb.append("transfer,");
        }
        if (provider.profitShareHandler() != null) {
            sb.append("profitShare,");
        }
        if (provider.billHandler() != null) {
            sb.append("bill,");
        }
        if (provider.callbackHandler() != null) {
            sb.append("callback,");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        return sb.append(']').toString();
    }
}
