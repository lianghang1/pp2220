package io.github.lianghang1.conflux.invoice.autoconfigure;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceAccountKey;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link InvoiceConfigProvider}。
 *
 * <p>启动期把 {@code conflux.invoice.apps.<appId>.channels.<channel>.*} 解析为
 * 渠道参数对象，建立三个方向的索引：
 * 「appId + 渠道 + 销方税号」（正向链路）、「渠道编码 + 账户键」（回调反查）、
 * 「应用 + 销方税号 → 渠道实例列表」（通道分级路由）。</p>
 *
 * <p><b>账户键唯一性 fail-fast</b>：账户键由
 * {@link InvoiceAccountKey#of(String, String)} 生成。两个配置生成相同账户键时
 * 立即抛 {@link InvoiceErrorCode#CONFIG_CONFLICT} 使启动失败，绝不静默覆盖——
 * 否则回调会归到错误企业或用错验签密钥。这与支付域修复后的做法一致。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesInvoiceConfigProvider implements InvoiceConfigProvider {

    private final Map<String, ChannelConfig> byApp = new HashMap<String, ChannelConfig>();
    private final Map<String, ChannelConfig> byAccount = new HashMap<String, ChannelConfig>();
    private final List<ChannelInstance> instances = new ArrayList<ChannelInstance>();

    public PropertiesInvoiceConfigProvider(InvoiceProperties properties,
                                           List<InvoiceChannelProvider> providers) {
        buildIndexes(properties, providers);
    }

    private void buildIndexes(InvoiceProperties properties, List<InvoiceChannelProvider> providers) {
        Map<String, InvoiceChannelProvider> providerByCode =
                new HashMap<String, InvoiceChannelProvider>();
        for (InvoiceChannelProvider provider : providers) {
            providerByCode.put(provider.channelCode(), provider);
        }

        for (Map.Entry<String, InvoiceProperties.AppProperties> appEntry
                : properties.getApps().entrySet()) {
            String appId = appEntry.getKey();
            InvoiceProperties.AppProperties appProps = appEntry.getValue();
            if (appProps == null || appProps.getChannels() == null) {
                continue;
            }
            for (Map.Entry<String, InvoiceProperties.ChannelProperties> channelEntry
                    : appProps.getChannels().entrySet()) {
                String channelCode = channelEntry.getKey();
                InvoiceProperties.ChannelProperties props = channelEntry.getValue() == null
                        ? new InvoiceProperties.ChannelProperties() : channelEntry.getValue();
                InvoiceChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("发票渠道[{}]未注册，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                Map<String, String> attrs = props.getAttrs() == null
                        ? new HashMap<String, String>() : props.getAttrs();
                InvoiceChannelParams params = provider.parseParams(attrs);
                if (params == null) {
                    log.warn("发票渠道[{}]不支持声明式配置，跳过应用[{}]的该渠道配置",
                            channelCode, appId);
                    continue;
                }
                String sellerTaxNo = StringUtils.isNotBlank(props.getSellerTaxNo())
                        ? props.getSellerTaxNo() : params.sellerTaxNo();
                String accountKey = InvoiceAccountKey.of(params.channelAppId(), sellerTaxNo);
                ChannelTier tier = resolveTier(props.getTier(), provider.channelTier());

                ChannelConfig config = new ChannelConfig()
                        .setAppId(appId)
                        .setChannelCode(channelCode)
                        .setChannelInstanceId(key(appId, channelCode, sellerTaxNo))
                        .setAccountKey(accountKey)
                        .setSellerTaxNo(sellerTaxNo)
                        .setNotifyUrl(props.getNotifyUrl())
                        .setSandbox(props.isSandbox())
                        .setTier(tier)
                        .setParams(params)
                        .setAttrs(attrs);
                byApp.put(key(appId, channelCode, sellerTaxNo), config);

                if (StringUtils.isBlank(accountKey)) {
                    log.warn("发票渠道[{}]应用[{}]未声明渠道侧应用标识，回调需使用方自行路由",
                            channelCode, appId);
                } else {
                    String indexKey = key(channelCode, accountKey);
                    ChannelConfig existing = byAccount.putIfAbsent(indexKey, config);
                    if (existing != null) {
                        throw new GatewayException(InvoiceErrorCode.CONFIG_CONFLICT,
                                "发票渠道账户键冲突，无法唯一定位回调配置: channel={}, accountKey={}, "
                                        + "appId={} 与 appId={} 生成了相同标识；"
                                        + "多企业接入请为各配置声明不同的 appKey / sellerTaxNo，"
                                        + "并在回调地址携带 accountKey 查询参数",
                                channelCode, accountKey, existing.getAppId(), appId);
                    }
                }

                instances.add(new ChannelInstance()
                        .setInstanceId(config.getChannelInstanceId())
                        .setAppId(appId)
                        .setChannelCode(channelCode)
                        .setAccountKey(accountKey)
                        .setSellerTaxNo(sellerTaxNo)
                        .setTier(tier)
                        .setPriority(props.getPriority())
                        .setEnabled(props.isEnabled())
                        .setConfig(config));
            }
        }
        log.info("发票渠道配置装载完成: appChannelConfigs={}, uniqueAccountKeys={}, instances={}",
                byApp.size(), byAccount.size(), instances.size());
        if (byAccount.size() != byApp.size()) {
            log.warn("有 {} 个配置未生成回调反查键（缺少 appKey 或被跳过），"
                            + "这些渠道的回调需使用方自行路由",
                    byApp.size() - byAccount.size());
        }
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
    public ChannelConfig getConfig(String appId, String channelCode, String sellerTaxNo) {
        ChannelConfig config = byApp.get(key(appId, channelCode, sellerTaxNo));
        if (config == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING,
                    "发票渠道配置缺失: appId={}, channel={}, taxNo={}",
                    appId, channelCode, sellerTaxNo);
        }
        return config;
    }

    @Override
    public ChannelConfig getByAccountKey(String channelCode, String accountKey) {
        ChannelConfig config = byAccount.get(key(channelCode, accountKey));
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

    private static String key(String left, String right) {
        return left + "::" + right;
    }

    private static String key(String a, String b, String c) {
        return a + "::" + b + "::" + c;
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
