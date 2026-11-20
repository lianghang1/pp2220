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
 * 配置提供者内存实现：以 configId + 渠道 + 销方税号为索引，维护可路由实例列表。
 *
 * <p>配置定位权在使用方：正向调用与回调均按 configId + channelCode + sellerTaxNo 取配置，
 * 框架不再从回调报文里隐式反查账户键。</p>
 *
 * @author lianghang
 */
public class InMemoryInvoiceConfigProvider implements InvoiceConfigProvider {

    private final Map<String, ChannelConfig> byConfig = new ConcurrentHashMap<String, ChannelConfig>();
    private final List<ChannelInstance> instances = new CopyOnWriteArrayList<ChannelInstance>();

    /** 注册一个渠道实例及其配置。 */
    public InMemoryInvoiceConfigProvider register(ChannelConfig config, ChannelInstance instance) {
        String key = ConfigKeys.key(config.getConfigId(), config.getChannelCode(), config.getSellerTaxNo());
        byConfig.put(key, config);
        if (instance != null) {
            if (instance.getConfig() == null) {
                instance.setConfig(config);
            }
            instances.add(instance);
        }
        return this;
    }

    @Override
    public ChannelConfig getConfig(String configId, String channelCode, String sellerTaxNo) {
        ChannelConfig config = byConfig.get(ConfigKeys.key(configId, channelCode, sellerTaxNo));
        if (config == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "发票渠道配置缺失: configId={}, channel={}, taxNo={}",
                    configId, channelCode, sellerTaxNo);
        }
        return config;
    }

    @Override
    public List<ChannelInstance> instances(String configId, String sellerTaxNo) {
        List<ChannelInstance> result = new ArrayList<ChannelInstance>();
        for (ChannelInstance instance : instances) {
            if (equal(configId, instance.getConfigId()) && equal(sellerTaxNo, instance.getSellerTaxNo())) {
                result.add(instance);
            }
        }
        return result;
    }

    private boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
