package io.github.lianghang1.conflux.invoice.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置提供者内存实现：装载期建立三维索引与账户键反查索引。
 *
 * <p><b>账户键唯一性 fail-fast</b>：与支付域修复后的
 * {@code PropertiesChannelConfigProvider} 同理，两个配置生成相同 accountKey 时
 * 立即抛 {@code CONFIG_CONFLICT} 使装配失败，绝不静默覆盖。</p>
 *
 * <p>主要供单测与 example 使用；starter 提供基于配置文件的实现。</p>
 *
 * @author lianghang
 */
public class InMemoryInvoiceConfigProvider implements InvoiceConfigProvider {

    private final Map<String, ChannelConfig> byApp = new ConcurrentHashMap<String, ChannelConfig>();
    private final Map<String, ChannelConfig> byAccount = new ConcurrentHashMap<String, ChannelConfig>();
    private final List<ChannelInstance> instances = new CopyOnWriteArrayList<ChannelInstance>();

    /** 注册一个渠道实例及其配置；账户键冲突时立即失败。 */
    public InMemoryInvoiceConfigProvider register(ChannelConfig config, ChannelInstance instance) {
        String appKey = StoreSupport.key(config.getAppId(), config.getChannelCode(),
                config.getSellerTaxNo());
        byApp.put(appKey, config);

        if (config.getAccountKey() != null) {
            String accountIndexKey = StoreSupport.key(config.getChannelCode(), config.getAccountKey());
            ChannelConfig existing = byAccount.putIfAbsent(accountIndexKey, config);
            if (existing != null) {
                throw new GatewayException(InvoiceErrorCode.CONFIG_CONFLICT,
                        "发票渠道账户键冲突: channel={}, accountKey={}, appId={} 与 appId={} 生成了相同标识",
                        config.getChannelCode(), config.getAccountKey(), existing.getAppId(),
                        config.getAppId());
            }
        }
        if (instance != null) {
            if (instance.getConfig() == null) {
                instance.setConfig(config);
            }
            instances.add(instance);
        }
        return this;
    }

    @Override
    public ChannelConfig getConfig(String appId, String channelCode, String sellerTaxNo) {
        ChannelConfig config = byApp.get(StoreSupport.key(appId, channelCode, sellerTaxNo));
        if (config == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "发票渠道配置缺失: appId={}, channel={}, taxNo={}",
                    appId, channelCode, sellerTaxNo);
        }
        return config;
    }

    @Override
    public ChannelConfig getByAccountKey(String channelCode, String accountKey) {
        ChannelConfig config = byAccount.get(StoreSupport.key(channelCode, accountKey));
        if (config == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "发票渠道账户键无对应配置: channel={}, accountKey={}", channelCode, accountKey);
        }
        return config;
    }

    @Override
    public List<ChannelInstance> instances(String appId, String sellerTaxNo) {
        List<ChannelInstance> result = new ArrayList<ChannelInstance>();
        for (ChannelInstance instance : instances) {
            if (equal(appId, instance.getAppId()) && equal(sellerTaxNo, instance.getSellerTaxNo())) {
                result.add(instance);
            }
        }
        return result;
    }

    private boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
