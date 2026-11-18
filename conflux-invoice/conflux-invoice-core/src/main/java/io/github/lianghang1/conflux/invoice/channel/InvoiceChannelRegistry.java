package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发票渠道注册表：渠道编码 → 渠道提供者，及能力处理器查找入口。
 *
 * <p>注册发生在启动期，运行期只读；查找缺失能力时抛明确异常而非返回 null。
 * 票种支持度也在此校验，避免把不支持的票种发给渠道后拿到含糊的错误。</p>
 *
 * @author lianghang
 */
@Slf4j
public class InvoiceChannelRegistry {

    private final Map<String, InvoiceChannelProvider> providers =
            new ConcurrentHashMap<String, InvoiceChannelProvider>();

    /** 注册渠道提供者；编码重复时保留先注册者并告警。 */
    public void register(InvoiceChannelProvider provider) {
        String code = provider.channelCode();
        InvoiceChannelProvider existing = providers.putIfAbsent(code, provider);
        if (existing != null) {
            log.warn("发票渠道重复注册，保留先注册者: channel={}, keep={}, ignore={}",
                    code, existing.getClass().getName(), provider.getClass().getName());
            return;
        }
        log.info("发票渠道已注册: channel={}, tier={}, types={}, capabilities={}",
                code, provider.channelTier(), provider.supportedTypes(), describeCapabilities(provider));
    }

    /** 按编码取渠道提供者；未注册抛 CHANNEL_NOT_FOUND。 */
    public InvoiceChannelProvider provider(String channelCode) {
        InvoiceChannelProvider provider = channelCode == null ? null : providers.get(channelCode);
        if (provider == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_NOT_FOUND,
                    "发票渠道未注册: {}", channelCode);
        }
        return provider;
    }

    public Set<String> registeredChannels() {
        return providers.keySet();
    }

    public ChannelTier tier(String channelCode) {
        return provider(channelCode).channelTier();
    }

    /** 校验渠道是否支持该票种；空集合表示不限制。 */
    public void assertTypeSupported(String channelCode, InvoiceType type) {
        Set<InvoiceType> supported = provider(channelCode).supportedTypes();
        if (type == null || supported == null || supported.isEmpty() || supported.contains(type)) {
            return;
        }
        throw new GatewayException(InvoiceErrorCode.INVOICE_TYPE_NOT_SUPPORTED,
                "渠道[{}]不支持票种[{}]", channelCode, type.name());
    }

    public InvoiceIssuer issuer(String channelCode) {
        return capability(channelCode, provider(channelCode).issuer(), "蓝字开票");
    }

    public InvoiceQuerier querier(String channelCode) {
        return capability(channelCode, provider(channelCode).querier(), "查单轮询");
    }

    public InvoiceRedHandler redHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).redHandler(), "红字");
    }

    public InvoiceFileFetcher fileFetcher(String channelCode) {
        return capability(channelCode, provider(channelCode).fileFetcher(), "版式文件下载");
    }

    public AuthSessionHandler authHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).authHandler(), "授权会话");
    }

    public InvoiceCallbackHandler callbackHandler(String channelCode) {
        return capability(channelCode, provider(channelCode).callbackHandler(), "回调解析");
    }

    public InvoiceDeliverer deliverer(String channelCode) {
        return capability(channelCode, provider(channelCode).deliverer(), "发票交付");
    }

    public InvoiceRepairer repairer(String channelCode) {
        return capability(channelCode, provider(channelCode).repairer(), "发票修复");
    }

    private <T> T capability(String channelCode, T handler, String capabilityName) {
        if (handler == null) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                    "渠道[{}]不支持{}能力", channelCode, capabilityName);
        }
        return handler;
    }

    private String describeCapabilities(InvoiceChannelProvider provider) {
        StringBuilder sb = new StringBuilder("[");
        append(sb, "issue", provider.issuer() != null);
        append(sb, "query", provider.querier() != null);
        append(sb, "red", provider.redHandler() != null);
        append(sb, "file", provider.fileFetcher() != null);
        append(sb, "auth", provider.authHandler() != null);
        append(sb, "callback", provider.callbackHandler() != null);
        append(sb, "delivery", provider.deliverer() != null);
        append(sb, "repair", provider.repairer() != null);
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        return sb.append(']').toString();
    }

    private void append(StringBuilder sb, String name, boolean present) {
        if (present) {
            sb.append(name).append(',');
        }
    }
}
