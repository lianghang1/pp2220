package com.sfi.conflux.invoice.autoconfigure;

import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.invoice.channel.BuiltinInvoiceChannels;
import com.sfi.conflux.invoice.channel.ChannelConfig;
import com.sfi.conflux.invoice.channel.ChannelInstance;
import com.sfi.conflux.invoice.channel.InvoiceChannelProvider;
import com.sfi.conflux.invoice.config.InvoiceConfigProvider;
import com.sfi.conflux.invoice.model.ChannelTier;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link InvoiceConfigProvider}。
 *
 * <p>启动期把 {@code conflux.invoice.configs.<configId>.<channelCode>.*} 解析为渠道参数对象，
 * 建立两个方向的索引：「configId + 渠道 + 销方税号」→ 配置（正向调用与回调验签）、
 * 「配置 + 销方税号 → 渠道实例列表」（通道分级路由）。</p>
 *
 * <p>配置定位权在使用方：回调不再从报文隐式反查账户键，改由业务按
 * configId + channelCode + sellerTaxNo 显式取配置。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesInvoiceConfigProvider implements InvoiceConfigProvider {

    private final Map<String, ChannelConfig> byConfig = new HashMap<String, ChannelConfig>();
    private final List<ChannelInstance> instances = new ArrayList<ChannelInstance>();

    public PropertiesInvoiceConfigProvider(InvoiceProperties properties,
                                           List<InvoiceChannelProvider> providers) {
        buildIndexes(properties, providers);
    }

    private void buildIndexes(InvoiceProperties properties, List<InvoiceChannelProvider> providers) {
        Map<String, InvoiceChannelProvider> providerByCode =
                new HashMap<String, InvoiceChannelProvider>();
        // 内置渠道（SPI 清单）先入表，自定义渠道后入表覆盖——与 registry 的"自定义优先"一致
        for (InvoiceChannelProvider provider : BuiltinInvoiceChannels.all()) {
            providerByCode.put(provider.channelCode(), provider);
        }
        if (providers != null) {
            for (InvoiceChannelProvider provider : providers) {
                providerByCode.put(provider.channelCode(), provider);
            }
        }

        for (Map.Entry<String, Map<String, InvoiceProperties.ChannelProperties>> configEntry
                : properties.getConfigs().entrySet()) {
            String configId = configEntry.getKey();
            Map<String, InvoiceProperties.ChannelProperties> channels = configEntry.getValue();
            if (channels == null) {
                continue;
            }
            for (Map.Entry<String, InvoiceProperties.ChannelProperties> channelEntry
                    : channels.entrySet()) {
                String channelCode = channelEntry.getKey();
                InvoiceProperties.ChannelProperties props = channelEntry.getValue() == null
                        ? new InvoiceProperties.ChannelProperties() : channelEntry.getValue();
                InvoiceChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("发票渠道[{}]未注册，跳过配置[{}]的该渠道配置", channelCode, configId);
                    continue;
                }
                Map<String, String> attr = props.getAttr() == null
                        ? new HashMap<String, String>() : props.getAttr();
                ChannelConfig config = provider.parseParams(attr);
                if (config == null) {
                    log.warn("发票渠道[{}]不支持声明式配置，跳过配置[{}]的该渠道配置",
                            channelCode, configId);
                    continue;
                }
                // 渠道 parseParams 已把 attr 中的 sellerTaxNo/sandbox 解析到强类型 config；
                // 顶层显式配置优先，未配置时沿用渠道从 attr 解析出的值
                String sellerTaxNo = StringUtils.isNotBlank(props.getSellerTaxNo())
                        ? props.getSellerTaxNo() : config.getSellerTaxNo();
                ChannelTier tier = resolveTier(props.getTier(), provider.channelTier());

                config.setConfigId(configId)
                        .setChannelCode(channelCode)
                        .setChannelInstanceId(key(configId, channelCode, sellerTaxNo))
                        .setSellerTaxNo(sellerTaxNo)
                        .setNotifyUrl(props.getNotifyUrl())
                        .setSandbox(props.isSandbox())
                        .setTier(tier)
                        .setAttr(attr);
                byConfig.put(key(configId, channelCode, sellerTaxNo), config);

                instances.add(new ChannelInstance()
                        .setInstanceId(config.getChannelInstanceId())
                        .setConfigId(configId)
                        .setChannelCode(channelCode)
                        .setSellerTaxNo(sellerTaxNo)
                        .setTier(tier)
                        .setPriority(props.getPriority())
                        .setEnabled(props.isEnabled())
                        .setConfig(config));
            }
        }
        log.info("发票渠道配置装载完成: configChannelConfigs={}, instances={}",
                byConfig.size(), instances.size());
    }

    private ChannelTier resolveTier(String configured, ChannelTier fallback) {
        if (StringUtils.isBlank(configured)) {
            return fallback;
        }
        try {
            return ChannelTier.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的通道分级[{}]，回退到渠道默认值[{}]", configured, fallback);
            return fallback;
        }
    }

    @Override
    public ChannelConfig getConfig(String configId, String channelCode, String sellerTaxNo) {
        ChannelConfig config = byConfig.get(key(configId, channelCode, sellerTaxNo));
        if (config == null) {
            throw new com.sfi.conflux.common.exception.GatewayException(
                    com.sfi.conflux.invoice.exception.InvoiceErrorCode.CONFIG_MISSING,
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

    private static String key(String a, String b, String c) {
        return a + "::" + b + "::" + c;
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
